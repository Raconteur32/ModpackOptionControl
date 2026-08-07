# Proposal: fix-web-session-ignore-expiry

## Why

The web GUI's ignore dialog offers a "Session — until the next finalized patch" kind, but the backend never expires session ignores: `IgnoreStore.resetSession()` exists in `gui_web` and is called nowhere. Session ignores therefore behave as permanent ignores, silently hiding changes from the author forever — the dangerous kind of wrong, because the UI explicitly promises they will resurface. The desktop GUI expires them correctly on draft finalize and amend-stash.

## What Changes

- `POST /api/draft/finalize`: after a successful finalize, expire session ignores and broadcast `ignores_changed` (the frontend already refetches ignores and diff on that event — hidden entries resurface in all connected clients).
- `POST /api/draft/finalize-for-amend`: same expiry + broadcast after a successful stash.
- **Unchanged**: recomposition/amend *finalization* does **not** expire session ignores (parity with the desktop GUI — history surgery is not the end of the author's patch-building session). Recomposition-scoped ignores keep their existing clear-on-start/cancel/finalize behavior.
- No changes to the desktop GUI, common, or fabric.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: adds an explicit ignore-lifetime contract — session ignores expire when a draft is finalized or stashed for amend (not on recomposition finalize); value ignores prune when the value changes; permanent ignores persist; directory ignores live in `moc.json`.

## Impact

- **Code**: `gui_web/.../routing/DraftRoutes.kt` — two call sites gain `IgnoreStore.resetSession()` + `EventBus.broadcast("ignores_changed")`. No frontend changes (event handling already refetches ignores + diff).
- **Tests**: new route tests — finalize expires session ignores; finalize-for-amend expires them; recomposition finalize preserves them (locks in the semantic); `ignores_changed` broadcast on both draft endpoints.
- **Compatibility**: none — this only deletes ignore entries the UI already told the user would be deleted. Interaction with `extract-authoring-from-common` is trivial (both touch `DraftRoutes.kt`; independent lines).
