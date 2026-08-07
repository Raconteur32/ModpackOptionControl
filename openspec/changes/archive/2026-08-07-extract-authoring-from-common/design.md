# Design: extract-authoring-from-common

## Context

`common` currently mixes two audiences: the runtime engine (used by the Fabric mod's preLaunch path) and the patch-authoring engine (used only by the desktop `gui` and the web `gui_web`). Grep-verified: `fabric/` has **zero** references to `DraftPatch`, `RecompositionDraft`, or `McInstanceRefMocFileSystem`; the only wrong-direction edge inside common is `DiffUtils.applyDiffToDraft` (common→DraftPatch). `gui_web` is uncommitted new work intended to replace `gui` (deprecated once feature-complete). Per-tool duplication of authoring code is an accepted pattern here (`IgnoreStore` precedent). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- `common` contains only runtime-engine code; the Fabric mod jar no longer embeds authoring machinery.
- Both tools own behaviorally identical copies of the authoring engine, except gui_web's conditional ref regeneration.
- On-disk authoring state remains byte-compatible between the two tools for the deprecation overlap.

**Non-Goals:**
- No behavior change in `gui` (always-regenerate stays) or `fabric`.
- No redesign of draft/recomposition semantics, persistence formats, or state locations.
- No deduplication effort between the two copies — gui's copy is explicitly throwaway.
- gui_web's startup does not gain gui's other hygiene steps (`IgnoreStore.pruneRedundant`, legacy `dev-ref` cleanup) — out of scope here.

## Decisions

### D1: Move the whole authoring cluster, including `McInstanceRefMocFileSystem`

`DraftPatch` and `RecompositionDraft` both depend on the dev-ref singleton (`config/moc/dev/ref`), and the dev ref is authoring-only. Moving the drafts without it would leave the leak in place. **Alternative considered:** moving only the two draft classes — rejected, it keeps an authoring singleton in common.

### D2: Copy, don't share

Each tool gets its own package-adjusted copy (`fr.raconteur.moc.gui.*` / `fr.raconteur.moc.web.*`). **Alternative considered:** a fourth shared "authoring" Gradle module depended on by both tools — rejected by the owner: gui is deprecated, its copy is dead-on-arrival, and a shared module would keep coupling alive past its usefulness.

### D3: `applyDiffToDraft` moves out; `isDescendant`/`directChildren` stay

`applyDiffToDraft` is 4 lines mapping `OptionDiff` to draft mutators — copied into both tools (or inlined at call sites). The path helpers are pure generic math with no authoring dependency; they remain in common's `DiffUtils`. **Alternative:** moving all of `DiffUtils` — rejected, duplicates generic utilities for no architectural gain.

### D4: Fingerprint-based conditional regeneration (gui_web only)

- **Fingerprint inputs** (hashed, e.g. SHA-256, over a canonical concatenation): `patch-list.json` content (order matters), every `patchs/**/patch.json` + `mocmeta.json` (sorted relative paths + bytes), `MocSettings.ignoredPaths`, and the application version.
- **Stamp location**: inside the ref tree at `config/moc/dev/ref/mocfsmetas/refstamp.json`.
- **Write protocol**: regenerate → then write stamp. Missing stamp ⇒ stale. Crash mid-regen leaves no/invalid stamp ⇒ next start regenerates. Fail-safe by construction.
- **Why content hash over mtimes**: `git checkout` rewrites mtimes without changing content; patch files are small, hashing is cheap relative to a full tree wipe + patch replay.
- **Known interaction**: gui's unconditional regen wipes the ref root — including the stamp — so gui_web regenerates once after each gui run. Accepted: fail-safe direction, disappears with gui. **Alternative considered:** stamp outside the wiped tree (`config/moc/dev/refstamp.json`) — rejected: a crashed gui regen would leave a valid-looking stamp over a half-built ref (fail-deadly).

### D5: Tests follow the living copy

`DraftPatchWorkflowTest` moves to gui_web (package-adjusted). gui's copy gets no new tests — it is deprecated and behavior-frozen. gui_web's existing route tests (`WebTestBase`, `DraftRoutesTest`, `RecompRoutesTest`) re-import the moved singletons; they already manipulate them directly, so no test-logic changes. New tests cover the fingerprint logic: fresh stamp skips regen, edited patch triggers regen, missing stamp regenerates, version bump regenerates.

### D6: Sequencing — gui_web first, gui second, delete last

1. Copy cluster into gui_web + fingerprint logic + tests → gui_web green.
2. Mechanical copy into gui, imports only → gui green.
3. Delete the cluster + `applyDiffToDraft` from common → full build green, fabric jar verified to still apply patches (common test suite + a preLaunch smoke test in a dev instance).

Deletion is last so every step leaves a buildable repo. **Alternative:** delete from common first and fix downstream — rejected, breaks both tools simultaneously.

## Risks / Trade-offs

- [The two copies drift apart during the overlap period] → Accepted by design; mitigated by the spec'd compatibility invariant (both tools must keep the on-disk contract) and by gui being behavior-frozen — changes happen only in gui_web, and only format-compatible ones.
- [Fingerprint misses an input that affects the ref tree, serving a stale ref] → Inputs were chosen by tracing what regen replays: patch contents, list order, ignored paths (affect scan/write semantics), app version (logic changes). If a new input emerges, adding it to the fingerprint is a one-line change with a safe failure mode (extra regen).
- [gui_web reads a ref tree built by an *older* gui run whose stamp was wiped] → Covered: missing stamp ⇒ regenerate.
- [Package rename breaks serialization] → Verified: no class names are persisted (JSON/JSON5 formats only), so renames are safe.
- [gui's copy is untested dead code] → Accepted; it is byte-for-byte the previously tested implementation with only package/import changes, and it is deprecated.
