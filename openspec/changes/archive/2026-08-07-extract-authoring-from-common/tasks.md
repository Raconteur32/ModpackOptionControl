# Tasks: extract-authoring-from-common

## 1. gui_web: embed the authoring engine

- [x] 1.1 Copy `DraftPatch`, `RecompositionDraft`, and `McInstanceRefMocFileSystem` from `common` into `gui_web` under `fr.raconteur.moc.web` packaging, adjusting imports to the new package
- [x] 1.2 Copy `DiffUtils.applyDiffToDraft` into gui_web (or inline at call sites)
- [x] 1.3 Re-point all gui_web routes/services (`DiffRoutes`, `DraftRoutes`, `RecompRoutes`, `IgnoreRoutes`, `Diffs.kt`) and `WebTestBase` to the embedded classes
- [x] 1.4 Move `DraftPatchWorkflowTest` from common to gui_web, package-adjusted, and confirm it passes against the embedded copy

## 2. gui_web: fingerprint-based conditional ref regeneration

- [x] 2.1 Implement fingerprint computation (SHA-256 over `patch-list.json` content, sorted `patchs/**/patch.json`+`mocmeta.json` paths+bytes, `MocSettings.ignoredPaths`, app version)
- [x] 2.2 Implement stamp read/write at `config/moc/dev/ref/mocfsmetas/refstamp.json`, written only after a completed regeneration
- [x] 2.3 Wire startup: regenerate dev ref only when the stamp is missing or mismatched
- [x] 2.4 Add tests: fresh stamp skips regen; edited `patch.json` triggers regen; reordered patch list triggers regen; missing stamp regenerates; version bump regenerates; crash-mid-regen (no stamp) regenerates

## 3. gui: embed the frozen copy

- [x] 3.1 Copy the same three classes + `applyDiffToDraft` into `gui` under `fr.raconteur.moc.gui` packaging, imports adjusted, behavior identical (unconditional regen preserved)
- [x] 3.2 Re-point gui call sites (`Main.kt`, `AppState`, `PatchesState`, screens) to the embedded classes
- [x] 3.3 Verify the desktop app builds and its manual smoke path works (open instance, stage draft, finalize, recompose)

## 4. common: delete the authoring cluster

- [x] 4.1 Delete `versioning/DraftPatch.kt`, `versioning/RecompositionDraft.kt`, `filesystem/McInstanceRefMocFileSystem.kt`, and `DiffUtils.applyDiffToDraft` from common (keep `isDescendant`/`directChildren`)
- [x] 4.2 Delete `DraftPatchWorkflowTest` from common (moved in 1.4); fix the stale comment in `MocSettings.kt`
- [x] 4.3 Run the full common test suite — runtime behavior must be unchanged

## 5. Verification

- [x] 5.1 Full multi-module build green: `:common:test`, `:gui_web:test`, `:fabric:build`, `:gui` compile
- [x] 5.2 Inspect the built Fabric jar: no `DraftPatch`/`RecompositionDraft`/`McInstanceRefMocFileSystem` classes present
- [x] 5.3 Cross-tool compatibility check: create a draft in gui_web, confirm gui restores it on the same instance (and vice versa for a recomposition session)
- [x] 5.4 Smoke-test preLaunch patch application in a dev instance (pending patches apply, applied-patches record intact)
- [x] 5.5 `openspec validate extract-authoring-from-common` passes
