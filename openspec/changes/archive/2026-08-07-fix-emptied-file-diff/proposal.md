# Fix emptied-file diff visibility

## Why

A managed JSON file whose content is emptied (or becomes invalid, or is reduced to a bare scalar like `42`/`null`) currently becomes a **ghost change**: the file list reports it as `CHANGED`, but the per-file diff tree is empty ("No differences in this file.") and staging the root fails with `option_not_in_diff`. The change can neither be inspected nor captured into a patch — the only capturable representation is a file `DELETION`, which has different semantics on target instances. Root cause: unreadable content under the pinned `content` type takes the "file deleted" branch of `MocFile.diffFrom` (flat content is `null`), producing the `""` deletion marker on an existing file — and the web diff tree never renders the root node `$`, hiding even legitimate root-level changes (`[]`, new empty files).

## What Changes

- **A — Raw-content fallback in flat-content computation** (`common`): when a file exists but its content cannot be parsed under its effective content type, `getFlatContent` SHALL fall back to the `text` reading (the raw string at root `$`) instead of `null`. The stored content type is **not** modified — the fallback is computed, never persisted, so a file automatically recovers its structured type when its content parses again. This makes emptied/invalid/scalar-root files produce a honest `$`-rooted diff (visible, stageable) and reserves the `""` deletion marker for genuinely absent files.
- **B — Patch metadata records the effective content type** (`common` contract, implemented in both `gui` and `gui_web` draft finalization): when a patch is authored over a file whose pinned type could not parse its content, the patch's `mocmeta.json` SHALL record `content=text` for that file — the type under which the value was actually captured. Without this, a "file emptied" patch applies as the two-character JSON literal `""` instead of an empty file (verified empirically).
- **C — Conditional root-node rendering** (`gui_web`): the per-file diff tree SHALL render the root node `$` when it carries a diff entry of its own — e.g. when it is the only entry (root replaced by a scalar/array/empty string) or when the `to` value replaces the whole root — so changes like `$ → ""` or `$ → []` are visible and stageable from the option tree.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `common`: the flattened option model gains the raw-content fallback rule; option-level diffing gains the rule that the `""` marker is reserved to absent files; the patch storage format gains the effective-content-type rule for `mocmeta.json`.
- `gui_web`: the "Diff exposure" requirement gains conditional rendering of the root `$` node in per-file option trees.

## Impact

- **Code**: `common/.../content/ContentType.kt` (`getFlatContent`), `gui_web/.../web/DiffTreeBuilder` (`Diffs.kt`), draft finalization in **both** `gui_web/.../web/DraftPatch.kt` and `gui/.../DraftPatch.kt` (duplicated implementations), possibly minimal `diff.js` adjustment.
- **Shared engine**: `gui` (desktop) consumes the same `common` `MocFile`/`diffFrom` — fix A applies to both GUIs automatically; desktop already has a partial root-value view, fix C is web-specific alignment.
- **APIs**: no route changes; per-file diff payloads gain a root `DiffNode` in the conditions above. WebSocket events unchanged.
- **On-disk formats**: no migration. `mocmetadata.json` is no longer mutated by this fix (unpersisted fallback); patch `mocmeta.json` may now carry `content=text` for files captured through the fallback.
- **Known behavior to document**: option-level entries of older patches targeting a file whose content is currently unreadable keep their pre-existing application semantics (resurrection as an empty object via `getContent() ?: Json5Object()`); nothing changes there.

## Non-goals (future tracks)

- Letting the user **view and override a file's content type from the UI** (manual escape hatch; will become its own change).
- Replacing the various `null`-returning read paths (`getContent`, bare-scalar roots, etc.) with **explicit, user-surfaced errors** — to be explored and specified separately.
