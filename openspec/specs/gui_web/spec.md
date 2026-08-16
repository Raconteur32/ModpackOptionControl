# Web GUI Specification

## Purpose

The `gui_web` module is MOC's browser-based authoring tool: a Ktor server hosting a REST API and WebSocket event stream, plus a dependency-free JavaScript single-page app. It offers the same patch-authoring workflows as the desktop GUI over the same on-disk state, additionally supporting multiple concurrent browser clients via live events.

## Requirements

### Requirement: Server startup and configuration

The server SHALL install its platform service, run migrations, run patch-list startup cleanup, prune redundant ignore entries (entries under an ignored directory or covered by a broader permanent/session/value ancestor), apply pending patches, then serve the SPA and API on port `MOC_PORT` (default 7421), opening a browser tab unless `MOC_NO_BROWSER=true` or desktop browsing is unsupported. The game directory resolves from an override variable, the `moc.gameDir` system property, or candidate probing (`.`, `..`, `run`, `../fabric/run`, `../run`); a valid directory contains both `config/` and `mods/`. Failure to resolve SHALL exit with code 1.

At startup the server SHALL regenerate the dev reference tree **only when it is stale**, determined by comparing a stored fingerprint against the current authoring inputs: the ordered patch list, the contents of every patch's `patch.json` and `mocmeta.json`, the configured ignored paths, and the application version. The fingerprint stamp SHALL be written only after a regeneration completes successfully, so an interrupted regeneration forces a full regeneration at the next start.

#### Scenario: Port from environment
- **WHEN** `MOC_PORT=8000` is set
- **THEN** the server listens on port 8000

#### Scenario: Fresh fingerprint skips regeneration
- **WHEN** the server starts and no patch file, the patch list, the ignored paths, or the app version has changed since the last completed regeneration
- **THEN** the dev reference tree is reused as-is and startup does not replay any patches

#### Scenario: Out-of-band patch edit triggers regeneration
- **WHEN** a patch's `patch.json` is edited outside the tool (or the patch list is reordered) while the server is stopped
- **THEN** the next startup detects the fingerprint mismatch and fully regenerates the dev reference tree before serving requests

#### Scenario: Missing or incomplete state regenerates
- **WHEN** the fingerprint stamp is absent (fresh install, prior crash mid-regeneration, or the reference tree was wiped by another tool)
- **THEN** a full regeneration runs and the stamp is written only on completion

#### Scenario: Redundant ignores pruned at startup
- **WHEN** the server starts with ignore entries covered by a broader ignore (e.g. a session ignore on an option whose ancestor is permanently ignored, or any entry under an ignored directory)
- **THEN** the covered entries are removed and the persisted store is updated; entries not covered by any broader ignore are kept

### Requirement: REST API surface

The server SHALL expose a JSON API under `/api/` covering: instance diff (`GET /api/diff`, `GET /api/diff/{file}`), draft (`GET/DELETE /api/draft`, `POST/DELETE /api/draft/entries`, `POST /api/draft/finalize`, `POST /api/draft/finalize-for-amend`), patches (`GET /api/patches`, `GET /api/patches/{name}`, `DELETE /api/patches`), ignores (`GET/POST/DELETE /api/ignores`, `GET/POST/DELETE /api/ignores/recomp`), and recomposition sessions (`GET/POST/DELETE /api/recomp`, `GET /api/recomp/diff[/{file}]`, `GET/POST/DELETE /api/recomp/entries`, `POST /api/recomp/finalize`).

Error semantics: invalid arguments (including unknown enum values) yield `400 {"error": "<code>"}`; missing resources yield `404`. Responses omit null fields.

#### Scenario: Finalize validation order
- **WHEN** `POST /api/draft/finalize` is called
- **THEN** failures surface in order: `empty_draft`, then `invalid_name`, then `name_taken`

#### Scenario: Patch name policy
- **WHEN** a finalize request carries a name not matching `^[A-Za-z0-9._-]+$`
- **THEN** the request fails with `invalid_name`

#### Scenario: Bulk patch deletion is all-or-nothing
- **WHEN** `DELETE /api/patches` lists any name that does not exist
- **THEN** the request fails with `patch_not_found` listing all missing names and nothing is deleted

### Requirement: Recomposition session rules via API

Starting a session requires a valid index range (`index_out_of_range` otherwise). Finalizing checks in order: `no_active_session`, `unresolved_conflicts`, `empty_draft`, `invalid_name`, `name_taken` — where a name already inside the replaced range is allowed. Staging an entry in a session defaults its mode to DEFAULT, maps deleted options to DELETION entries, and resolves that option's conflict; the `action: "ignore"` variant records a session-scoped ignore (DIRECTORY kind delegates to the global directory-ignore handling) and also resolves the conflict.

#### Scenario: Unresolved conflicts block finalization
- **WHEN** a session has conflicting entries that were neither staged nor ignored
- **THEN** `POST /api/recomp/finalize` fails with `unresolved_conflicts`

### Requirement: Server-side staging overlap invariant

The server SHALL maintain a no-overlap invariant on staged entries: within the same file, two staged entries may not have equal-or-nested option paths (except the file-deletion marker `""`, which overlaps nothing). Staging an entry SHALL atomically remove any already-staged entries whose option path is an ancestor or descendant of the new entry's path, then upsert the new entry. This applies to both the draft (`POST /api/draft/entries`) and recomposition sessions (`POST /api/recomp/entries`), and SHALL hold regardless of which client performs the calls.

#### Scenario: Staging a child replaces the staged parent
- **WHEN** `$['a']` is staged and a client stages `$['a']['b']` via the API
- **THEN** the staged set contains only the new entry for `$['a']['b']`

#### Scenario: Staging a parent replaces staged descendants
- **WHEN** `$['a']['b']` and `$['a']['c']` are staged and a client stages `$['a']` via the API
- **THEN** the staged set contains only the new entry for `$['a']`

#### Scenario: Entries in other files are untouched
- **WHEN** an entry in `a.json` is staged and a client stages an entry in `b.json`
- **THEN** the `a.json` entry remains staged

#### Scenario: Exact-key restaging updates in place
- **WHEN** an option is staged and a client stages the same option path with a different mode
- **THEN** the entry is updated in place (no removal side effects)

#### Scenario: File deletion marker coexists with value entries
- **WHEN** a file-deletion entry (`""`) is staged for a file and a client stages a value entry in the same file
- **THEN** both entries remain staged (deletion-then-recreate semantics are preserved)

#### Scenario: Recomposition provenance follows removed entries
- **WHEN** a recomposition staging removes an auto-populated entry as an overlapped ancestor or descendant
- **THEN** the removed entry's source-patch provenance is discarded with it

### Requirement: WebSocket event stream

The server SHALL broadcast JSON events `{"type": ...}` to all connected clients on `/ws`: `draft_changed`, `patches_changed`, `diff_changed`, `ignores_changed`, `recomp_changed`, and `conflicts_changed` (with `count`). Event granularity SHALL reflect what actually changed: entry-level ignore changes emit `ignores_changed` only; directory ignores also emit `diff_changed`; starting a session emits `recomp_changed` only. Incoming client frames are ignored; send failures are swallowed.

#### Scenario: Multi-client freshness
- **WHEN** one client stages a draft entry
- **THEN** every connected client receives `draft_changed` and refetches the draft

### Requirement: Diff exposure

Diff endpoints SHALL serve the diff between the live instance and the dev reference as file summaries (ordered CHANGED, then NEW, then DELETED, alphabetical within group) and per-file option trees with labels, kinds, old/new values, staged/ignored annotations, and (in sessions) conflict and source-patch provenance. Before every diff response, stale value ignores are pruned. With `showAll=true`, the diff is computed against an empty file system so every option appears as NEW.

Every per-file option tree SHALL be rooted at a single root node: the `$` node for an existing file, or the `(file)` deletion-marker node (`""`) for a deleted file. The root node is a regular node — it carries its label, kind, staged/ignored annotations, and (when leaf) old/new values, and it SHALL be stageable, ignorable and resettable through the same per-row and bulk machinery as any other option. Staging the root node stages the whole file content (its container value), which is semantically distinct from staging its children individually (only the changed, displayed options).

A node that has children SHALL NOT carry serialized old/new values in the tree payload, and the client SHALL NOT render values for such a node. A leaf node — including an atomic replacement, whose former children's Deleted records remain in the underlying flat diff (they drive apply-time matching) but are not displayed — SHALL carry and render its old/new values. This applies uniformly to every node, including the root: the container-summary record at `$` (root object on both sides, changes only beneath it) produces a root node with children and no values.

#### Scenario: Staged annotation wins
- **WHEN** an option is both staged and ignored
- **THEN** the node reports the staged action (DEFAULT/OVERRIDE)

#### Scenario: Root node always present
- **WHEN** a file's root is an object on both sides of the diff and only options below `$` changed
- **THEN** the tree's top level is the CHANGED root node `$` itself, carrying no old/new values, with the changed options as its children

#### Scenario: Container-summary root stays implicit
- **WHEN** a file's root is an object on both sides of the diff and only options below `$` changed
- **THEN** the container-summary record at `$` produces a root node with children and no old/new values (the root node is structural; it exposes no container value of its own)

#### Scenario: Emptied JSON file shows a root change
- **WHEN** a managed JSON file whose previous content was an object is now empty
- **THEN** the per-file diff tree shows a single CHANGED root node whose new value is `""`, and that node can be staged like any other option

#### Scenario: Root replaced by an array is visible
- **WHEN** a managed JSON file's root changed from an object to an array (including `[]`)
- **THEN** the per-file diff tree shows the CHANGED root node with its old and new values and no children

#### Scenario: Atomic replacement hides former children at any depth
- **WHEN** any option node (including the root) changes from an object to a non-object value — e.g. `{"test": {"test": "test"}}` becoming `{"test": "test"}`
- **THEN** the tree shows only the CHANGED node with its old and new values; the former children's Deleted records remain in the underlying diff (they drive apply-time matching) but are not displayed

#### Scenario: Deleted file shows a single (file) root node
- **WHEN** a managed file no longer exists
- **THEN** the per-file diff tree is a single DELETED root node labelled `(file)`, carrying its former value, stageable and ignorable like any other node

#### Scenario: Staging the root stages the whole file
- **WHEN** the user stages the root node `$` of a changed file
- **THEN** the resulting patch entry captures the entire file content at `$` — including unchanged and ignored options — rather than only the options visible in the tree

#### Scenario: Node with children carries no values
- **WHEN** a diff node has children (e.g. the container-summary root of a large config file)
- **THEN** the diff response does not serialize that node's old/new values and the client renders no value column for it

### Requirement: Ignore rule lifetimes

Ignore kinds SHALL have distinct, observable lifetimes:

- **Session** ignores SHALL expire when a draft patch is finalized into a patch and when a draft is stashed for amend. They SHALL NOT expire on recomposition or amend finalization (history surgery does not end the author's patch-building session), and SHALL NOT expire on server restart.
- **Value** ignores SHALL be pruned automatically when the ignored option's live value no longer equals the recorded target value.
- **Permanent** ignores SHALL persist until explicitly removed.
- **Directory** ignores SHALL be stored as ignored paths in `moc.json` (not in the ignore store) and persist until explicitly removed.
- **Recomposition-session** ignores SHALL be cleared when a recomposition/amend session starts, is cancelled, or is finalized.

When session ignores expire, the server SHALL broadcast `ignores_changed` so all connected clients refetch and previously hidden changes resurface.

#### Scenario: Session ignores expire on patch finalize
- **WHEN** a draft containing staged entries is finalized into a patch while session ignores exist
- **THEN** all session ignores are removed and `ignores_changed` is broadcast

#### Scenario: Session ignores expire on amend stash
- **WHEN** a draft is stashed for amend via `POST /api/draft/finalize-for-amend` while session ignores exist
- **THEN** all session ignores are removed and `ignores_changed` is broadcast

#### Scenario: Session ignores survive recomposition finalize
- **WHEN** a recomposition or amend session is finalized while session ignores exist
- **THEN** the session ignores are preserved (only recomposition-session ignores are cleared)

#### Scenario: Session ignores survive restart
- **WHEN** the server restarts with unexpired session ignores
- **THEN** they are still in effect (they are not tied to the process lifetime)

#### Scenario: Value ignore self-prunes
- **WHEN** a diff endpoint is served and a value ignore's recorded target no longer matches the option's live value
- **THEN** the stale value ignore is removed and the change becomes visible

### Requirement: Embedded authoring engine with shared-state compatibility

The web backend SHALL embed its own implementation of the patch-authoring engine (draft staging, recomposition, dev reference tree) rather than relying on the core runtime module to provide it. Except for conditional reference regeneration, its behavior SHALL remain identical to before the extraction, and it SHALL keep reading and writing the established on-disk authoring state formats and locations so that it remains interchangeable with the desktop GUI on the same instance.

#### Scenario: State interchangeable with the desktop GUI
- **WHEN** a draft or recomposition session is created with the web GUI and the desktop GUI is later opened on the same instance
- **THEN** the desktop GUI restores that draft or session exactly, and vice versa

### Requirement: Session and draft persistence parity

The web backend SHALL operate on the same on-disk state as the desktop GUI (`config/moc/patches/`, `dev/patch-draft.json`, `dev/recomposition-draft.json`, `dev/amend/`, `dev/editor.json`), so either tool sees the other's patches, drafts, and ignores. Drafts and sessions persist across restarts and are revalidated on load.

#### Scenario: Tools share state
- **WHEN** a patch is finalized in the desktop GUI and the web app is opened on the same instance
- **THEN** the web patch history lists the new patch

### Requirement: Browser client

The shipped SPA SHALL provide the complete authoring workflow without a build step: a mode badge (NEW PATCH / AMEND / RECOMPOSITION) driven by session state, directory-grouped file tree, expandable diff tree with per-row and bulk stage/ignore actions, a staging panel with per-entry mode switching and source provenance, patch history with view/amend/recompose/delete (single and bulk), ignore management popovers, conflict-aware confirmation flows, and automatic refresh driven by the WebSocket event map with reconnect.

Multi-select gestures follow standard web conventions within a scope (same level, same parent). Select-all (Ctrl/Cmd+A) with an existing selection SHALL select every visible row in that selection's scope; with no selection, it SHALL select the root node's direct children — never the root node alone, so that the quickest gesture always means "every changed top-level option, individually" and not "the whole file".

#### Scenario: Amend entry points
- **WHEN** the user amends
- **THEN** they can start either from staged draft entries (finalize-for-amend then session) or directly from the last patch in history, and an unsaved-draft prompt offers Keep or Overwrite in the latter case

#### Scenario: Finalize gating in the UI
- **WHEN** a session has unresolved conflicts
- **THEN** the Finalize action is disabled until every conflict is staged or ignored

#### Scenario: Select-all without selection targets the root's children
- **WHEN** the user presses Ctrl/Cmd+A in the diff tree with no current selection
- **THEN** every visible direct child of the root node is selected, and the root node itself is not

#### Scenario: Select-all with selection keeps its scope
- **WHEN** the user presses Ctrl/Cmd+A in the diff tree with an existing selection
- **THEN** every visible row sharing the selection's scope (same level, same parent) is selected

### Requirement: State-aware action dropdown with RESET

The browser client SHALL model each per-row action dropdown as a state label plus a state-dependent menu of actions. The state SHALL be one of UNSTAGED (button rendered empty), DEFAULTED, OVERRIDDEN, or IGNORED, derived from the staged entry or matching ignore rule for that file/option. The menu SHALL offer:

- UNSTAGED: DEFAULT, OVERRIDE, IGNORE
- DEFAULTED or OVERRIDDEN: DEFAULT, OVERRIDE, IGNORE, RESET
- IGNORED: RESET

RESET SHALL return the option to UNSTAGED without confirmation: for a staged option it SHALL remove the staged entry (draft in NEW PATCH mode, recomposition entries in AMEND/RECOMPOSITION mode); for an ignored option it SHALL remove the matching ignore rule, using the rule's known kind — SESSION, VALUE, or PERMANENT via the general ignores endpoint, or the recomposition-scoped ignores endpoint for recomp ignores. RESET SHALL reuse existing API routes only.

#### Scenario: Unstage a staged option from the diff tree
- **WHEN** the user opens the dropdown of a DEFAULTED option and clicks RESET
- **THEN** the staged entry is removed without any confirmation dialog and the option returns to UNSTAGED

#### Scenario: Unignore an ignored option from the diff tree
- **WHEN** the user opens the dropdown of an IGNORED option
- **THEN** the menu offers only RESET, and clicking it removes the matching ignore rule without confirmation and the option returns to UNSTAGED

#### Scenario: RESET on a staged option in AMEND mode
- **WHEN** the user clicks RESET on an entry staged in an AMEND or RECOMPOSITION session (including entries originating from the amended patch)
- **THEN** the entry is removed via the recomposition entries endpoint without confirmation, with the same semantics as the staging panel remove button

#### Scenario: UNSTAGED button stays visually empty
- **WHEN** an option has no staged entry and no matching ignore rule
- **THEN** its dropdown button renders with no label text, as before

### Requirement: Staging clears recomposition-scoped ignores

When an option is staged (single or bulk) in an AMEND/RECOMPOSITION session, any recomposition-scoped ignore for that option SHALL be removed: the server SHALL remove it atomically as part of the stage operation so the invariant holds for any client, and the browser client SHALL additionally detect the recomp ignore when planning the stage and include the same "will be un-ignored" warning it shows for other ignore kinds. After such a stage, unstaging the option SHALL return it to UNSTAGED (never back to IGNORED).

#### Scenario: Stage over a recomp ignore with warning
- **WHEN** the user stages (or bulk-stages) an option that carries a recomposition-scoped ignore
- **THEN** the confirmation popup lists the un-ignore effect as for other kinds, and after confirming, both the staged entry exists and the recomp ignore is gone from the recomp-ignores list

#### Scenario: Server-side invariant
- **WHEN** a stage request reaches `POST /api/recomp/entries` for an option holding a recomp ignore (from any client, even one that did not remove the ignore itself)
- **THEN** the recomp ignore is removed atomically with the stage and an `ignores_changed` event is broadcast

#### Scenario: Unstage after stage-over-ignore returns to UNSTAGED
- **WHEN** the user stages an option that carried a recomp ignore and later unstages it (RESET or the staging panel remove button)
- **THEN** the option returns to UNSTAGED, not IGNORED

### Requirement: Bulk RESET action

The bulk action dropdowns (file tree, main diff area, staging panel) SHALL offer RESET in addition to DEFAULT, OVERRIDE, and IGNORE. Bulk RESET SHALL apply the per-row inverse across the whole selection — unstaging staged rows and unignoring ignored rows — without confirmation, reusing the same per-row semantics as the single-row RESET.

#### Scenario: Bulk reset a mixed selection
- **WHEN** the user selects multiple rows mixing staged and ignored options and picks RESET in the bulk dropdown
- **THEN** every staged row is unstaged and every ignored row is unignored, with no confirmation dialog

### Requirement: Directory-level ignore action

Directory rows in the file tree SHALL expose an ignore control that applies a DIRECTORY ignore for that directory path, with the same effects as the former popup-based DIRECTORY choice (ignored path registered, staged entries under the directory removed, filesystems reloaded). The ignore-type popup for file/option rows SHALL no longer offer the DIRECTORY kind. The file tree SHALL NOT offer a directory-level unignore control: an ignored directory has no visible diff rows and its ignore rule SHALL remain removable from the Ignores popover.

#### Scenario: Ignore a directory from its tree row
- **WHEN** the user clicks the ignore control on a directory row
- **THEN** a DIRECTORY ignore is applied for that directory and its files disappear from the diff tree

#### Scenario: Ignore-type popup kinds
- **WHEN** the user picks IGNORE on a file row or option row
- **THEN** the ignore-type popup offers SESSION, VALUE, and PERMANENT only — no DIRECTORY choice

### Requirement: Dropdown menu stays within its scroll container

When an action dropdown menu would overflow the bottom edge of its enclosing scroll container, the menu SHALL open upward from the button instead of downward, so that every option remains clickable. This SHALL apply to all action dropdowns (diff tree, file tree, staging panel, bulk bars).

#### Scenario: Single-option diff tree
- **WHEN** the user opens the dropdown on the only row of a diff tree (or any row near the bottom of a scroll container)
- **THEN** the full menu is visible and every option is clickable

#### Scenario: Menu with room below still opens downward
- **WHEN** the user opens a dropdown whose row has enough space below it within the scroll container
- **THEN** the menu opens downward as before
