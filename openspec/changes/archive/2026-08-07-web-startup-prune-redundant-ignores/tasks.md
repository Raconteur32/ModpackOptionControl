# Tasks: web-startup-prune-redundant-ignores

## 1. Wiring

- [x] 1.1 Call `IgnoreStore.pruneRedundant()` in `gui_web/.../Main.kt` after `PatchList.runStartupCleanup()`, before `applyPending()`

## 2. Tests

- [x] 2.1 Test: session/value ignores covered by a permanent ancestor are pruned; non-covered entries kept
- [x] 2.2 Test: entries under an ignored directory are pruned (all three kinds); recomposition ignores untouched
- [x] 2.3 Test: no redundant entries → store file unchanged (no spurious save)

## 3. Verification

- [x] 3.1 `gradle :gui_web:test` green
- [x] 3.2 `openspec validate web-startup-prune-redundant-ignores` passes
