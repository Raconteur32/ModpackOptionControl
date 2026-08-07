# Proposal: rename-patchs-directory

## Why

The on-disk patch directory is misspelled: `config/moc/patchs/`. It is load-bearing — end-user clients, both authoring tools, and the migration code all resolve it — and it is scattered across five string literals in four files with no single source of truth. Fixing it is cheapest now: once `extract-authoring-from-common` copies `DraftPatch`/`RecompositionDraft` into both GUIs, the same rename would have to be applied identically in three modules under the shared-state compatibility constraint. This change must land before that one.

## What Changes

- Patch directory renamed to `config/moc/patches/`, resolved through a single constant used by `PatchList.patchDir`, `Patch.load`, `DraftPatch.finalize`, and `RecompositionDraft` (including its `temp-<name>` staging dir).
- `MocMigration` gains an idempotent, non-destructive step renaming a legacy `config/moc/patchs/` directory to `config/moc/patches/` (skipped when the destination exists; never overwrites). Every entry point (mod preLaunch, both tools) already runs migration, so existing installs migrate automatically on next launch.
- **Strict migration, no fallback read** (deliberate): an install running an *older* MOC version after migration will silently apply no patches. Accepted — modpacks pin their mod version.
- Unchanged: `patch-list.json`, `deleted-patch-list.json`, zip import/export layout (flat `patch.json` + `mocmeta.json`), and all patch entry semantics.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `common`: patch storage path changes from `moc/patchs/` to `moc/patches/`; migration gains the legacy-directory rename rule.

## Impact

- **Code**: `common` only — `PatchList.kt`, `Patch.kt`, `DraftPatch.kt`, `RecompositionDraft.kt`, `MocMigration.kt`. No frontend/API references exist (grep-verified: routes and JS never name the directory).
- **Tests**: `PatchListDeletionTest`, `DraftPatchWorkflowTest`, `MocMigrationTest`, `gui_web/WebTestBase` path updates; new migration test for the directory rename.
- **Users**: existing installs migrate transparently on next launch of the mod or either tool. Downgrades to pre-rename MOC versions lose patch application (accepted risk, see above).
- **Sequencing**: must be applied before `extract-authoring-from-common`.
