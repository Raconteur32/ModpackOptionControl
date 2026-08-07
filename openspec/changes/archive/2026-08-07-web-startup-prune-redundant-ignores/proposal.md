# Proposal: web-startup-prune-redundant-ignores

## Why

The desktop GUI prunes redundant ignore entries at startup (`IgnoreStore.pruneRedundant()` — entries under an ignored directory or covered by a broader permanent/session/value ancestor). The web backend has the same function (identical copy) but never calls it, so its `editor.json` accumulates redundant entries forever. Behavior is unaffected (matching is exact), but the file and the ignore panel counts grow with dead entries — the only startup hygiene step gui_web is missing.

## What Changes

- gui_web startup calls `IgnoreStore.pruneRedundant()` after `PatchList.runStartupCleanup()` and before `applyPending()` — mirroring gui's ordering (settings available, no client connected yet, no WebSocket broadcast needed).
- No change to the pruning logic itself, to gui, or to common.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: startup gains redundant-ignore pruning (one scenario on the startup requirement).

## Impact

- **Code**: one call in `gui_web/.../Main.kt`; new tests seeding redundant ignores and asserting they are pruned.
- **Compatibility**: none — this only deletes ignore entries that are already shadowed by broader ones.
