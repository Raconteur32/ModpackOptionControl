# Tasks: fix-web-session-ignore-expiry

## 1. Backend fix

- [x] 1.1 In `DraftRoutes.kt` `POST /api/draft/finalize`: after a successful `DraftPatch.finalize`, call `IgnoreStore.resetSession()` and broadcast `ignores_changed`
- [x] 1.2 In `DraftRoutes.kt` `POST /api/draft/finalize-for-amend`: after a successful stash, call `IgnoreStore.resetSession()` and broadcast `ignores_changed`

## 2. Tests

- [x] 2.1 Test: `POST /api/draft/finalize` removes session ignores and preserves value/permanent ignores
- [x] 2.2 Test: `POST /api/draft/finalize-for-amend` removes session ignores
- [x] 2.3 Test: `POST /api/recomp/finalize` preserves session ignores while clearing recomposition-session ignores (locks in the semantic)
- [x] 2.4 Test: failed finalize (e.g. `name_taken`) does not expire session ignores

## 3. Verification

- [x] 3.1 `gradle :gui_web:test` green
- [x] 3.2 Manual smoke: session-ignore a change in the web UI, finalize a patch, confirm the ignored change resurfaces (and that a second connected browser tab refreshes via the broadcast)
- [x] 3.3 `openspec validate fix-web-session-ignore-expiry` passes
