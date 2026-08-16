# Tasks: Unify the root option node `$`

## 1. Server: unified tree building (design D1, D2)

- [x] 1.1 In `DiffTreeBuilder.buildTree` (gui_web `Diffs.kt`), replace the object/atomic fork with a single root `buildNode` call: root path is `""` when the flat diff contains the file-deletion marker, else `"$"`
- [x] 1.2 In `DiffTreeBuilder.buildNode`, emit `oldValue`/`newValue` as `null` when the node has children (leaf-only value rule)
- [x] 1.3 Delete `buildDeletedFileTree`; make `DiffRoutes` and `RecompRoutes` call the unified `buildTree` for all file kinds (including DELETED)
- [x] 1.4 Verify `isFileIgnored` and diff summaries still behave (they read the flat diff directly, not the tree — no change expected)

## 2. Frontend: root as a regular row (design D3)

- [x] 2.1 In `diff.js` `renderNode`, remove the `isRoot` special case (value rendering and RAW toggle become leaf-only, matching the server payload)
- [x] 2.2 Verify the root row renders with kind badge, dropdown, checkbox and partial-stage badge like any other node; check the Filtered/Greyed ignore display on the root (`subtreeFullyIgnored`)

## 3. Frontend: select-all scoping (design D4)

- [x] 3.1 Change the Ctrl/Cmd+A no-selection fallback in `diff.js` so the scope is the root node's children (`selIndex.siblingsByScope` entry keyed by the root row), not the depth-0 rows
- [x] 3.2 Verify with-selection Ctrl/Cmd+A still selects the selection's scope

## 4. Tests

- [x] 4.1 Update `gui_web` JUnit tests asserting tree shapes (`DiffRoutesTest`, recomp tree tests in `RecompRoutesTest`) to the always-root shape; add coverage: container-summary root present without values, atomic root replacement leaf with values, deleted file single `(file)` root, staging `$` produces a whole-file entry
- [x] 4.2 Update/add vitest coverage for the select-all fallback scope (no selection → root's children, never the root alone)
- [x] 4.3 Run `./gradlew :common:test :gui_web:test` and `cd gui_web && npx vitest run`

## 5. Exploratory validation

- [x] 5.1 Manual run (`./web-gui-run.sh` or a fake instance): browse changed/new/deleted files, stage/unstage the root row, bulk-stage the root's children via Ctrl+A, ignore the root, recomposition view tree
