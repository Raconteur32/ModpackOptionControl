# Proposal: extract-authoring-from-common

## Why

The `common` module is jar-in-jar'd into the Fabric mod shipped to every end user, yet it contains patch-*authoring* machinery (`DraftPatch`, `RecompositionDraft`, `McInstanceRefMocFileSystem`) that only the authoring tools use — the mod's preLaunch path never touches it. With `gui_web` intended to replace the desktop `gui`, both tools need their own copy of this machinery anyway (the `IgnoreStore` duplication sets the precedent), so common should be reduced to the runtime engine now rather than at gui's deprecation.

## What Changes

- Move `DraftPatch`, `RecompositionDraft`, and `McInstanceRefMocFileSystem` out of `common` into **both** `gui` and `gui_web` as independent, package-adjusted copies (gui's copy behaviorally identical — "gui as is"; gui_web's copy is the living implementation).
- Move `DiffUtils.applyDiffToDraft` out of `common` (it is the one common→draft dependency; generic path helpers `isDescendant`/`directChildren` stay).
- **Compatibility invariant**: both copies keep reading/writing the identical on-disk state (`config/moc/dev/patch-draft.json`, `recomposition-draft.json`, `amend/`, `recomp-before/`, `recomp-after/`, `dev/ref/`) so gui and gui_web remain interchangeable on the same instance until gui is deprecated.
- In `gui_web` only: replace unconditional dev-ref regeneration with **fingerprint-based conditional regeneration** (option C) — regenerate `dev/ref` at startup only when patch state, ignored paths, or app version changed; gui keeps always-regenerate.
- Move `DraftPatchWorkflowTest` from common to gui_web (home of the living copy); common keeps its runtime tests.
- No behavior change in `fabric`; its jar simply stops embedding authoring code.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `common`: the draft-staging and recomposition requirements are removed from the core engine — they relocate to the authoring tools.
- `gui`: owns an embedded copy of the authoring engine; behavior unchanged, on-disk state compatibility with gui_web becomes an explicit requirement.
- `gui_web`: owns the living authoring engine; startup gains fingerprint-based conditional dev-ref regeneration (regenerate only when stale, crash-safe stamping).

## Impact

- **Code**: `common` loses `versioning/DraftPatch.kt`, `versioning/RecompositionDraft.kt`, `filesystem/McInstanceRefMocFileSystem.kt`, `DiffUtils.applyDiffToDraft`, and `DraftPatchWorkflowTest`; `MocSettings.kt` comment touch-up. `gui` and `gui_web` each gain the three classes + `applyDiffToDraft` under their own packages; all gui screens/state and gui_web routes/tests re-import.
- **Runtime artifact**: the Fabric mod jar shrinks (no authoring machinery shipped to end users). Zero behavioral change at preLaunch — fabric has no references to the moved classes (verified by grep).
- **State compatibility**: on-disk formats and paths are unchanged; both tools can operate on the same instance during the deprecation overlap. gui's always-regen wipes gui_web's fingerprint stamp, forcing a safe (if wasteful) regen at gui_web's next start — acceptable during overlap, disappears with gui.
- **Tests**: gui_web route tests (`WebTestBase`, `DraftRoutesTest`, `RecompRoutesTest`) re-import the moved singletons; `DraftPatchWorkflowTest` relocates; common's remaining suite must still pass.
