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

Diff endpoints SHALL serve the diff between the live instance and the dev reference as file summaries (ordered CHANGED, then NEW, then DELETED, alphabetical within group) and per-file option trees with labels, kinds, old/new values, staged/ignored annotations, and (in sessions) conflict and source-patch provenance. Before every diff response, stale value ignores are pruned. With `showAll=true`, the diff is computed against an empty file system so every option appears as NEW. Deleted files appear as a single node labelled `(file)`.

#### Scenario: Staged annotation wins
- **WHEN** an option is both staged and ignored
- **THEN** the node reports the staged action (DEFAULT/OVERRIDE)

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

#### Scenario: Amend entry points
- **WHEN** the user amends
- **THEN** they can start either from staged draft entries (finalize-for-amend then session) or directly from the last patch in history, and an unsaved-draft prompt offers Keep or Overwrite in the latter case

#### Scenario: Finalize gating in the UI
- **WHEN** a session has unresolved conflicts
- **THEN** the Finalize action is disabled until every conflict is staged or ignored
