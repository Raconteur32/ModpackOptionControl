# Design: Unify the root option node `$`

## Context

The `common` diff engine already records the root option: every changed file gets a container-summary `Changed` record at `$` whenever a leaf beneath it changes, and atomic root replacements (root → scalar/array/raw string) produce a `Changed` at `$` plus `Deleted` records for the former children. Deleted files are the one exception: a single `Deleted` record at `""` (the file-deletion marker), no `$`.

The special-casing lives entirely in the presentation layer of `gui_web`:

- `DiffTreeBuilder.buildTree` (Diffs.kt) forks on `flatDiff["$"].newValue is Json5Object`: object root → children only; otherwise → single synthesized root node.
- `DiffTreeBuilder.buildNode` holds the atomic-replacement no-children rule (already generic, works at any depth).
- `buildDeletedFileTree` builds the `""`/`(file)` tree separately.
- The frontend `renderNode` (diff.js) carries an `isRoot` special case (value visible even with children + RAW toggle).
- File-level actions bypass the option tree: `rootPathForFile` maps a file row to `$` (or `""`), staging/ignoring from the file tree.

Staging, ignore and reset pipelines are already path-generic: `/api/draft/entries` looks the path up in the flat diff, `DraftPatch.removeOverlapping` uses `isDescendant` (everything descends from `$`), `resolveIgnoreAction` works for `$`. No engine change is needed.

## Goals / Non-Goals

**Goals:**
- One tree-building path: every per-file tree is exactly one root node (`$`, or `""`/`(file)` for deleted files) built by the same recursive `buildNode`.
- One value rule: values are serialized and rendered iff the node is a leaf.
- The root is stageable/ignorable/resettable in the option tree like any other node.
- Select-all keeps its current "changed options individually" semantics.

**Non-Goals:**
- No change to `common` (diff engine, flat model, patch application) or `fabric`.
- No change to the desktop `gui` module — it is deprecated. Its drill-down model (the user navigates *inside* a node and sees its children; file-level actions stage `$`) already treats the root as first-class.
- No guardrail against staging `$` (whole-file entries). The semantics are legitimate and distinct from bulk-staging children; steering users toward granular entries is a best-practice/documentation matter.

## Decisions

### D1 — `buildTree` becomes a single `buildNode(root)` call

`DiffTreeBuilder.buildTree(flatDiff, …)` picks the root path — `""` when the flat diff contains the file-deletion marker, else `"$"` — and returns `listOf(buildNode(flatDiff, rootPath, …))`. The object/atomic fork disappears; the existing no-children rule inside `buildNode` (`Changed` to a non-object ⇒ no children) already covers atomic root replacements, including the emptied-file case (`""` new value). `buildDeletedFileTree` is deleted; `DiffRoutes` and `RecompRoutes` stop branching on `FileDiffKind.DELETED` for tree building.

The `(file)` label is produced by the existing `extractLabel("")` — no label special case needed.

Alternative considered: keep `buildDeletedFileTree` and only unify `$`. Rejected — it would preserve exactly the kind of parallel path this change removes, and the deletion-marker node satisfies the same node shape.

### D2 — Values are suppressed for nodes with children, server-side

In `buildNode`, when `children.isNotEmpty()`, `oldValue`/`newValue` are emitted as `null`. Rationale:

- The container-summary `Changed` at `$` (and at any container) carries the *entire* subtree as old/new values. Always emitting the root would serialize the whole file content twice into every `/api/diff/{file}` response — a real payload cost for large configs, for zero display value.
- A uniform leaf-only rule makes the frontend's `isRoot` special case unnecessary: `renderNode` already renders values only for leaf nodes once the atomic-replacement case is handled by the server sending a children-less node. The RAW toggle stays leaf-only.

Alternative considered: emit values but truncate/hide client-side. Rejected — pays the payload cost and keeps a client special case.

### D3 — The root row uses the existing row machinery unchanged

Dropdown, kind badge, partial-stage badge, selection checkbox, ignore states: `resolveRowState`, `planStage`, `requestBulkStage` and the REST endpoints are all path-generic and already correct for `$` (file-level staging from the file tree exercises the same endpoints today). The tree row simply exposes them. The root row is a depth-0 node, expanded by default, so its children render exactly like today's top-level rows.

Consequence: the file tree's per-file actions (`rootPathForFile`) now duplicate what the root row offers. They are kept (file-level bulk actions across files remain useful), but the option tree no longer depends on them.

### D4 — Select-all default scope is the root's children

In the frontend selection index, the per-file fallback scope (used by Ctrl/Cmd+A with no selection) SHALL list the root node's children, not the depth-0 rows. With `$` as the single depth-0 row, scoping to depth-0 would select the root alone, and a bulk action on it would stage the *whole file* — the exact opposite of today's "every changed top-level option, individually". With an existing selection, the scope remains that selection's scope (unchanged behavior).

Alternative considered: exclude the root row from selection entirely. Rejected — the root must stay selectable so it can be staged/ignored from the option tree; the fix belongs in the select-all fallback only.

### D5 — Desktop GUI untouched

The desktop is deprecated. Its navigation (`pathStack`, starting inside `$`) and its file-level actions (`applyCurrentFile` stages `$`) already give the root first-class treatment; adding a root row would only insert a one-item navigation level. The `filter { it != "$" }` call sites stay as they are.

## Risks / Trade-offs

- [Ctrl/Cmd+A silently becoming "stage the whole file"] → D4: the no-selection fallback scope is the root's children; covered by a spec scenario and tests.
- [Users staging `$` where granular entries would be better, collapsing patch granularity] → accepted: the semantics are legitimate (whole-file vs per-option), the confirmation popup already lists replaced child entries, and steering is a best-practice matter to handle with users, not a UI guardrail.
- [Diff responses no longer carry container values — a client that wanted them breaks] → only the bundled SPA consumes these endpoints; the rule is spec'd (Diff exposure).
- [Tests asserting today's tree shapes fail] → expected; `DiffRoutesTest` and recomp tests are updated to the always-root shape, plus new coverage for root staging and select-all scoping.
- [VALUE-kind ignore at `$` matches against the whole-file stringified value, which is meaningless] → pre-existing oddity (reachable from the file tree today), unchanged by this change; a whole-file VALUE ignore simply never matches.

## Migration Plan

Pure presentation-layer change; no persisted-state or API-shape migration. Diff DTOs change (root node always present, container values nulled) but are consumed only by the bundled SPA shipped in the same jar. Rollback = revert.

## Open Questions

(none)
