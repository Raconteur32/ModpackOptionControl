# Design: dropdown-reset-action

## Context

See proposal.md — Why. All work is in the `gui_web` SPA (`src/main/resources/static`), a no-build-step vanilla JS front rendered via `innerHTML` + global event delegation. The action dropdown (`js/dropdown.js`) is shared by the file tree, diff tree, and staging panel. Mutations already exist for every inverse path: `DELETE /api/draft/entries`, `DELETE /api/recomp/entries`, `DELETE /api/ignores`, `DELETE /api/ignores/recomp`. The server already annotates each `DiffNode` with `action` + `ignoreKind`; the client additionally holds `state.ignores.entries` (with kind) and `state.recompIgnores`.

## Goals / Non-Goals

**Goals**: state-aware menus with RESET (single + bulk); directory ignore moved to dir rows; menu flip to avoid clipping. Zero Kotlin/API change.

**Non-goals**: desktop GUI (`gui`, Compose) parity; changing the staging ✕ button (kept as-is, RESET is an additional path, not a replacement); directory-level unignore in the tree; re-select-current-mode no-op optimization (out of scope); keyboard navigation / a11y of the menu (deferred).

## Decisions

### D1 — Row state resolved client-side, in one place

Add a single `resolveRowState(filePath, optionPath)` helper in `actions.js` returning `{ state: 'UNSTAGED'|'DEFAULTED'|'OVERRIDDEN'|'IGNORED', ignoreKind? }`:

1. draft entry (`findDraftEntry`) → DEFAULTED/OVERRIDDEN from `entry.mode`
2. `state.ignores.entries` match → IGNORED with its `kind` (SESSION/VALUE/PERMANENT)
3. `state.recompIgnores` match → IGNORED, kind RECOMP
4. else UNSTAGED

This deliberately shadows the server-computed `DiffNode.action` for rendering (same inputs, same result) so the *menu composition* logic lives in one function shared by all three panels, instead of each renderer mapping `action` strings differently. `dropdown.js` receives `{ state, ignoreKind }` and renders menu items from a small table.

*Alternative*: keep passing the current `action` string and branch per call site — rejected, it scatters the state→menu mapping across four renderers.

### D2 — RESET as `requestReset(filePath, optionPath)` in `actions.js`

- Staged → `backing().remove({filePath, optionPath})` (identical to the staging ✕, already mode-aware via `backing()`).
- Ignored SESSION/VALUE/PERMANENT → `api.ignores.remove({filePath, optionPath, kind})`.
- Ignored RECOMP → `api.ignores.recomp.remove({filePath, optionPath})`.
- Kind DIRECTORY → unreachable per-option (directory-ignored files vanish from the diff); defensive no-op.
- No confirmation in any branch; ends with `reloadCallback()` like every other action.

*Alternative*: a dedicated server endpoint — rejected, zero added value over composing existing routes.

### D3 — Bulk RESET reuses the per-row inverse, batched

`requestBulkReset(targets)`: partition targets into staged vs ignored via D1's resolver, run the removes sequentially (same pattern as `executeBulkStage`), single `reloadCallback()` at the end. Rows already UNSTAGED are skipped silently.

### D4 — Directory ignore button on dir rows, no popup

Dir rows currently are pure collapse toggles (`renderDir`). Add a small icon button (`btn-icon`) at the right of `.dir-header` that calls `api.ignores.add({ filePath: <dir fullPath>, optionPath: '', kind: 'DIRECTORY' })` directly — this is exactly what the DIRECTORY branch of the popup did after `parentDirOf` remapping, minus the popup. `stopPropagation` so it doesn't toggle the collapse. No confirmation (status quo: the DIRECTORY radio never had one either, despite `removeEntriesUnder`). In AMEND/RECOMP the call still goes to the general `/api/ignores` route — DIRECTORY is filesystem-wide by design (see `RecompRoutes` ignore branch). Remove the DIRECTORY radio from `showIgnoreTypeDialog`; the `kind` plumbing stays for the three remaining kinds and bulk ignore.

### D5 — Flip measured at toggle time, stored in `uiState`

When a dropdown toggle is clicked, before `rerender()`: measure the button's `getBoundingClientRect()` against the rect of its nearest scroll-container ancestor (first ancestor with computed `overflow-y` of `auto`/`scroll`, falling back to the viewport). If space below < menu height and space above > space below, set a flip flag stored alongside the open id (e.g. `uiState.openDropdown = { id, up: true }`). Render adds a `.dropup` class; CSS flips to `bottom: calc(100% + 2px)`. Menu height is known (fixed option count × fixed row padding) so no pre-measure DOM hack is needed.

*Alternative A (fixed-position portal)*: immune to clipping but needs scroll/resize repositioning and breaks the "menu lives in the row's innerHTML" model — rejected as over-engineering for this case.
*Alternative B (CSS anchor positioning)*: not portable enough — rejected.

Because the flag is recomputed at every toggle, stale directions can't survive re-renders. Measuring against the *old* DOM at toggle time is correct: the rerender only swaps the menu in, rows don't move.

### D6 — Recomp-ignore cleanup on stage: server guarantee + client warning

Pre-existing gap surfaced by RESET: in AMEND/RECOMPOSITION, ignores live in `IgnoreStore.recompositionIgnores` (client mirror: `state.recompIgnores`), which `planStage`/`findIgnoreEntry` never consult, and `RecompositionDraft.setValueEntry`/`setDeletionEntry` never clear. Staging over a recomp ignore thus leaves a ghost rule: display stays correct (staged mode wins in `resolveIgnoreAction`) until the entry is unstaged — then the option flips back to IGNORED.

Two-layer fix (option C):
- **Server (the invariant)**: the non-ignore branch of `POST /api/recomp/entries` calls `IgnoreStore.removeRecomp(filePath, optionPath)` and broadcasts `ignores_changed` when a rule was actually removed. Same philosophy as `DraftPatch.removeOverlapping`: "server-side so it holds for any client, atomically" — including the desktop GUI, which shares this on-disk state.
- **Client (the announcement)**: `planStage` also consults `state.recompIgnores`, so the confirmation popup lists the same "Will be un-ignored" effect as for other kinds; `executeStage`/`executeBulkStage` then call `api.ignores.recomp.remove` explicitly. The explicit client call keeps UX parity with NEW_PATCH; the server call is the safety net if a client forgets.

No confirmation semantics change: the warning rides the existing stage-confirmation popup; when no other effect exists, staging over a recomp ignore still shows the popup (as staging over a session ignore does today).

## Risks / Trade-offs

- [IGNORED rows lose the direct "stage over the ignore" path in the single-row menu (previously DEFAULT/OVERRIDE un-ignored + staged behind one confirmation)] → Accepted UX simplification per user decision: RESET then stage is two clicks, always unambiguous. The combined path survives in bulk staging, which keeps un-ignore + stage behind one confirmation (now extended to recomp ignores via D6).
- [Staging panel rows gain RESET, duplicating the ✕ button] → Deliberate consistency; the ✕ stays.
- [Bulk RESET on a selection containing rows whose ignore rule is not visible (FILTERED mode hides fully-ignored subtrees, so those rows can't be selected anyway)] → No hidden side effects reachable via selection.
- [Pre-existing quirk: identical `dropdownId` for the same entry in diff tree and staging panel means toggling in one panel closes a menu opened in the other] → Out of scope; noted for a future polish change.

## Migration Plan

Pure client-side change; no persisted-state or API contract change. Draft/ignore files untouched. Rollback = revert the static assets.

## Open Questions

- Directory-row ignore button visibility: always visible vs. shown on row hover — cosmetic, decidable at implementation time without impacting specs or tasks.
