# Tasks: dropdown-reset-action

## 1. Row state resolution (actions.js)

- [x] 1.1 Add `resolveRowState(filePath, optionPath)` returning `{ state, ignoreKind? }` per design D1 (draft entry → DEFAULTED/OVERRIDDEN; `state.ignores` → IGNORED+kind; `state.recompIgnores` → IGNORED+RECOMP; else UNSTAGED)
- [x] 1.2 Add `requestReset(filePath, optionPath)` per design D2: draft remove via `backing().remove`, ignore remove via `api.ignores.remove` (kind) or `api.ignores.recomp.remove` (RECOMP), defensive no-op on DIRECTORY, no confirmation, ends with `reloadCallback()`
- [x] 1.3 Add `requestBulkReset(targets)` per design D3: partition staged/ignored via `resolveRowState`, batch removes, single reload; skip UNSTAGED rows

## 2. State-aware dropdown (dropdown.js)

- [x] 2.1 Change `renderActionDropdown` to take `{ state, ignoreKind }` and render the menu from the state→actions table: UNSTAGED → DEFAULT/OVERRIDE/IGNORE; DEFAULTED/OVERRIDDEN → DEFAULT/OVERRIDE/IGNORE + separated RESET item; IGNORED → RESET only
- [x] 2.2 Wire the RESET option through the delegated click handler to `requestReset` (single) — keep existing DEFAULT/OVERRIDE/IGNORE routing unchanged
- [x] 2.3 Add RESET to `renderBulkActionDropdown` option lists (main area, staging, file tree) and route it to `requestBulkReset` in the three registered bulk handlers
- [x] 2.4 Style the RESET item in `components.css` (muted/danger-adjacent, separator above when it follows other options)

## 3. Call-site migration to state model

- [x] 3.1 `diff.js` renderNode: pass `resolveRowState` result instead of `node.action`; keep patch-view read-only rows on their current display-only usage (no RESET on disabled dropdowns)
- [x] 3.2 `filetree.js` renderFileRow/fileAction: replace local derivation with `resolveRowState`
- [x] 3.3 `staging.js` renderEntry: pass state from the entry mode via `resolveRowState` (RESET coexists with the ✕ button)

## 4. Directory ignore relocation

- [x] 4.1 Remove the DIRECTORY radio from `showIgnoreTypeDialog` in `ignores.js` (kinds become SESSION/VALUE/PERMANENT; keep kind plumbing for bulk ignore)
- [x] 4.2 Add an ignore icon button to dir rows in `filetree.js` `renderDir` calling `api.ignores.add({ filePath: dirPath, optionPath: '', kind: 'DIRECTORY' })` directly, with `stopPropagation` so collapse toggle is not triggered, then reload
- [x] 4.3 Remove the now-dead DIRECTORY remapping branches (`parentDirOf`) from `executeIgnore`/`executeBulkIgnore` in `actions.js` if no caller can produce DIRECTORY anymore

## 5. Recomp-ignore cleanup on stage (design D6)

- [x] 5.1 Server: in `RecompRoutes.kt` `POST /api/recomp/entries` non-ignore branch, call `IgnoreStore.removeRecomp(filePath, optionPath)` and broadcast `ignores_changed` when a rule was actually removed
- [x] 5.2 Client: extend `planStage` to detect recomp ignores via `state.recompIgnores` and push the same "Will be un-ignored" confirmation effect as other kinds
- [x] 5.3 Client: `executeStage`/`executeBulkStage` remove recomp ignores explicitly via `api.ignores.recomp.remove` alongside the existing ignore removals
- [x] 5.4 Ktor test: staging an option holding a recomp ignore removes it and broadcasts `ignores_changed`; unstage afterwards leaves the option UNSTAGED

## 6. Dropdown flip (clipping fix)

- [x] 6.1 On dropdown toggle click (single + bulk), measure button rect vs nearest scroll-container ancestor (computed `overflow-y` auto/scroll, viewport fallback) and store the open state as `{ id, up }` in `uiState` per design D5
- [x] 6.2 Render `.dropup` on the menu when `up` is set; CSS in `components.css` flips to `bottom: calc(100% + 2px)`
- [x] 6.3 Verify flip works in all three scroll contexts: `#diff-tree` (single-option tree), file tree panel, `#staging-list`

## 7. Tests & validation

- [x] 7.1 Add/extend vitest coverage for the state→menu mapping and RESET routing (existing JS tests live in `gui_web`, run with `npx vitest run`)
- [x] 7.2 Manual exploratory pass per AGENTS.md (fake instance, `MOC_NO_BROWSER=true MOC_PORT=7599`): RESET on staged/ignored/recomp-ignored rows, bulk RESET on mixed selection, stage-over-recomp-ignore warning + cleanup, directory ignore button, popup without DIRECTORY radio, flip at container bottom
- [x] 7.3 `openspec validate dropdown-reset-action` clean
