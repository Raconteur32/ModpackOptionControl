# Proposal: Unify the root option node `$`

## Why

The flat diff already contains the root option `$` for every changed file (container-summary `Changed` record, per the `common` flattened option model). But the presentation layer drops it and re-creates it ad hoc: `DiffTreeBuilder.buildTree` hides the root when it is an object and synthesizes a single root node for atomic replacements, the frontend carries an `isRoot` special case, deleted files go through a separate `buildDeletedFileTree`, and file-level actions live in the file tree via `rootPathForFile` while option-level actions live in the option tree. That is ~6 special cases compensating for one design gap: the root is not a first-class node. The semantic difference between "stage the whole file" (`$`) and "stage these options" (children) is a real, meaningful distinction that the UI currently hides in two different areas instead of expressing structurally as parent vs children.

## What Changes

- The per-file option tree SHALL always be rooted at a single root node: `$` for existing files, the `(file)` deletion marker (`""`) for deleted files. No more object/atomic-replacement fork in tree building; `buildDeletedFileTree` is absorbed into the common path.
- Value display rule: a node that has children SHALL NOT serialize or render old/new values; a leaf node (including an atomic root replacement, whose former children stay hidden) renders them. This removes the whole-file value payload from diff responses and the frontend `isRoot` special case.
- The root node is selectable, stageable (DEFAULT/OVERRIDE), ignorable and resettable through the same per-row and bulk machinery as any other option. Staging `$` stages the whole file content (container value), which is semantically distinct from bulk-staging its displayed children (only changed, non-hidden options) — both remain possible.
- Select-all (Ctrl/Cmd+A) scoping: with no prior selection, the scope SHALL be the root's children (never the root alone); with an existing selection, the scope remains that selection's level.
- Desktop GUI (`gui`): unchanged — it is deprecated, and its drill-down navigation plus file-level actions already give the root first-class treatment.
- `common` diff engine: unchanged — the diff already carries `$`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: the **Diff exposure** requirement changes — the root node is always present in per-file option trees (the "container-summary root stays implicit" behavior is reversed), and the value-serialization rule for nodes with children is specified. The **Browser client** requirement gains the select-all scoping rule.

## Impact

- `gui_web/src/main/kotlin/fr/raconteur/moc/web/Diffs.kt`: `DiffTreeBuilder.buildTree` simplified to a single root `buildNode`; `buildDeletedFileTree` removed; value suppression for nodes with children.
- `gui_web/src/main/resources/static/js/diff.js`: `isRoot` special case removed; root row rendered like any node.
- `gui_web/src/main/resources/static/js/diff.js` / `selection.js`: default select-all scope targets the root's children.
- `gui_web` routing (`DiffRoutes.kt`, `RecompRoutes.kt`): deleted-file tree built through the unified path.
- Tests: `gui_web` JUnit tests asserting tree shapes (`DiffRoutesTest`, recomp tests) and vitest selection tests updated; new coverage for root staging and select-all scoping.
- Out of scope: `common`, `fabric`, `gui` (desktop, deprecated).
