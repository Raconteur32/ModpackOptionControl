# Tasks: rename-patchs-directory

## 1. Single source of truth

- [x] 1.1 Introduce a shared constant for the patch directory name (e.g. on `PatchList` or a paths holder) and re-point `PatchList.patchDir`, `Patch.load`, `DraftPatch.finalize`, and `RecompositionDraft` (patch root + `temp-<name>` staging dir) to resolve `config/moc/patches/` through it

## 2. Migration

- [x] 2.1 Add a `MocMigration` step renaming legacy `config/moc/patchs/` to `config/moc/patches/`, only when the destination does not exist; idempotent and non-destructive per the existing migration rules
- [x] 2.2 Add migration tests: legacy dir renamed with contents intact; second run is a no-op; both-dirs-present leaves both untouched

## 3. Test updates

- [x] 3.1 Update `PatchListDeletionTest`, `DraftPatchWorkflowTest`, and `MocMigrationTest` to the new path
- [x] 3.2 Update `gui_web/WebTestBase` patch-dir cleanup to the new path

## 4. Verification

- [x] 4.1 `gradle :common:test :gui_web:test` green
- [x] 4.2 Smoke: instance with a legacy `patchs/` dir launches the mod (or a tool), patches migrate and pending ones still apply
- [x] 4.3 `openspec validate rename-patchs-directory` passes
