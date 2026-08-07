# Design — fix-emptied-file-diff

## Context

Exploratory testing (Playwright + API against a scratch instance) established the failure chain for a managed JSON file that is emptied:

```
meta pins content=json  →  JsonContentType.getContent("") = null
→  getFlatContent() = null  →  MocFile.diffFrom takes the "file deleted" branch
→  single record at "" (deletion marker) on a CHANGED file
→  DiffTreeBuilder.buildChildren("$") never sees ""  →  empty tree
→  staging $ rejected (option_not_in_diff, 404)
```

A second experiment proved a round-trip hazard for any fix that leaves the pinned type in place at authoring time: a hand-crafted patch capturing `$ → ""` with `mocmeta` `content=json` applied on a fresh instance writes the two characters `""` into the file (JSON-quoted empty string), not an empty file — because `applyPatch` writes via the patch-declared type while the value was captured through a raw-text reading.

Key facts grounding the decisions:

- `MocFile`/`diffFrom`/`getFlatContent` live in `common`; `gui` (desktop) depends on `:common` and has **no** engine copy — a fix in `common` covers both GUIs. **Except** draft finalization (`DraftPatch`), which is duplicated: `gui_web/.../web/DraftPatch.kt` and `gui/.../DraftPatch.kt`.
- `DraftPatch.finalize`/`finalizeForAmend` snapshot the *persisted* `mocmetadata.json` into the patch's `mocmeta.json`.
- `TextContentType.getContent` wraps the raw string in a primitive; it returns `null` only when the file does not exist — and `MocFile.getFlatContent` already guards `!exists`, so the fallback never interferes with genuine deletions.
- `json5Reader.parse` yields no element for bare scalars (`42`, `null`, `"s"`), so scalar-rooted JSON files take the same unreadable path as empty content.
- Desktop already has a partial root display: `AppState` opens a "value view" when `$` has no sub-options. Fix C aligns the web UI.

## Goals / Non-Goals

**Goals:**
- An emptied/invalidated file produces a honest, stageable `$`-rooted diff.
- A patch capturing such a file round-trips byte-faithfully on target instances.
- The web diff tree renders the root node `$` when it carries its own record.
- Structured typing self-heals when content becomes valid again.

**Non-Goals:**
- UI to view/override a file's content type (future change).
- Replacing `null`-returning read paths with explicit user-surfaced errors (future exploration).
- Desktop (Compose) root-node rendering alignment beyond what already exists.
- Changing `json5Reader` scalar-root parsing semantics.

## Decisions

### A1 — Fallback in `MocFile.getFlatContent`, never persisted, recorded as a first-class fact

The fallback lives in `MocFile.getFlatContent` (not `ContentType`, which is a stateless singleton and cannot remember anything): try the pinned type's flat content, else the `text` flat content. Crucially, `MocFile` **caches which type actually produced the flat content** (`effectiveContentType`), so the "did we fall back?" condition exists in exactly one place and every downstream consumer reads the recorded fact instead of re-deriving it. `MocFile.getFlatContent`'s `!exists` guard is untouched, so the `""` deletion-marker branch of `diffFrom` remains reachable only for absent files — the spec rule "marker only for absent files" holds by construction, with no change inside `diffFrom`.

Rationale over the earlier load-time revalidation design:
- **Self-healing**: the stored type is never rewritten; a file whose content parses again immediately recovers option-level granularity. The persisted variant was a one-way trap (text accepts everything, so a persisted `text` pin could never be re-inferred).
- **No startup cost**: no parse is added to the scan; the fallback happens at diff time, where parsing already occurs.
- **No sticky false negatives**: a spurious parse failure (transient I/O) has no durable effect.
- **No re-inference at finalize** (see A2).

### A2 — Patch `mocmeta.json` records the *effective* type, read from the cached fact

At draft finalization, the metadata written for each touched file is the stored metadata with `content` replaced by the file's `effectiveContentType()` when they differ — i.e. `text` when the value was captured through the raw-content fallback. Finalize never re-parses and never re-tests the fallback condition: it reads the fact cached by the diff-time read (with a warm-on-demand accessor as a defensive fallback). Applies to `finalize`, `finalizeForAmend`, and the recomposition/amend finalization path — in **both** `DraftPatch`/`RecompositionDraft` copies (`gui_web` and `gui`), kept behaviorally identical by sharing a single `common` helper.

Rationale: the patch must declare the type under which its values were captured, or `applyPatch` re-encodes them under the wrong format (the demonstrated `""` literal instead of an empty file). This persists the type only at authoring time — where persistence is correct and needed — while load-time typing stays non-destructive.

Alternatives considered:
- *Persist the fallback at load (earlier design)*: one-way trap, startup parse cost, sticky false negatives. Superseded by A1+A2.
- *Fix `JsonContentType.setContent` to write raw strings for primitive roots*: breaks legitimate JSON string roots; conflates capture and writing concerns. Rejected.
- *Fix `diffFrom` to special-case unreadable content*: leaves the file typed `json` while diffing it as text — a lie that would leak into patch metadata. Rejected.

### C1 — Root node rendered in `DiffTreeBuilder`, not in JS; atomic-replacement rule generalized to any depth

`GET /api/diff/{file}` builds its tree via a new `DiffTreeBuilder.buildTree`. The diff engine records a container-summary `Changed` at every object node with leaf changes beneath it — including `$` — so "has a record" is NOT the right display condition. The rule is **atomic replacement**: a node (root or nested) whose new value is not an object is rendered as a single node with no children; its former children's `Deleted` records stay in the flat diff (they drive `matchesRef` at apply time — without them a pending DEFAULT deletion would resurrect an emptied file as `{}`) but are not displayed. A container summary (object on both sides) renders no row of its own.

Rationale: putting the rule server-side keeps API payloads self-describing (testable at the API level) and avoids teaching the frontend new semantics. Generalizing to nested nodes (not just `$`) fixes the same redundancy one level down (`{"test": {"test": "test"}}` → `{"test": "test"}`) and matches the engine's own array atomicity (`cutBranch`).

Alternatives considered:
- *Cut the children records in the engine (`common`)*: changes apply-time semantics (see above). Rejected — presentation-only cut.
- *Frontend-only rendering*: payload stays lossy-looking, desktop/web rules drift. Rejected.
- *Always render a root row*: noisy; the "container summary stays implicit" scenario forbids it. Rejected.

### C2 — The `""` marker on CHANGED files needs no special handling

After A1, an existing-but-unreadable file no longer produces `""`. `buildDeletedFileTree` remains used solely for genuinely DELETED files. No change.

## Implementation Sketch

Exact before/after shape of every touchpoint (~50 lines total surface). OpenSpec artifacts don't hold diffs by design — this sketch is the reviewable contract for the apply phase.

### 1. `common/.../filesystem/MocFile.kt` — fallback + recorded fact (~10 lines)

```kotlin
// BEFORE
fun getFlatContent(): FlatContent? {
    if (!exists) return null
    return contentType.getFlatContent(this)
}

// AFTER — the ONLY place the fallback condition exists
private var computedEffectiveType: ContentType? = null

fun getFlatContent(): FlatContent? {
    if (!exists) return null
    contentType.getFlatContent(this)
        ?.also { computedEffectiveType = contentType; return it }
    return TextContentType.getFlatContent(this)
        ?.also { computedEffectiveType = TextContentType }
}

// Reads the recorded fact; warms the cache if no diff has run yet
// (defensive — staging/recomposition always diff before finalizing).
fun effectiveContentType(): ContentType =
    computedEffectiveType ?: contentType.also { getFlatContent() }
```

`ContentType.getFlatContent` keeps its original `?: return null` shape — the fallback does not live there. The `!exists` guard is untouched, so genuine deletions still yield `null` → `""` marker.

### 2. `common/.../filesystem/MocFileSystem.kt` — shared effective-metadata helper (new)

```kotlin
// NEW — single implementation consumed by all four finalization call sites
// (draft + recomposition, gui_web and gui). Pure reader of the cached fact:
// no parsing, no re-derived condition.
fun effectiveMetadataFor(filePaths: Set<String>): Map<String, Map<String, String>> =
    filePaths.mapNotNull { fp -> getFileMetadata(fp)?.let { fp to it } }.toMap()
        .mapValues { (fp, meta) ->
            val eff = _files[Path.of(fp)]?.effectiveContentType()?.id
            if (eff != null && eff != meta["content"]) meta + ("content" to eff) else meta
        }
```

### 3. `gui_web/.../web/DraftPatch.kt` — `finalize` + `finalizeForAmend`

```kotlin
// BEFORE (both methods)
val allMeta: Map<String, Map<String, String>> = try {
    gson.fromJson(McInstanceMocFileSystem.getMetadataFile().toFile().readText(), metaType) ?: emptyMap()
} catch (_: Exception) { emptyMap() }
val filteredMeta = allMeta.filter { (key, _) -> key in patchFilePaths }

// AFTER
val filteredMeta = McInstanceMocFileSystem.effectiveMetadataFor(patchFilePaths)
```

### 4. `gui/.../gui/DraftPatch.kt` — mirror of (3), identical edit.

### 5. `RecompositionDraft.finalize` (gui_web + gui copies)

```kotlin
// BEFORE
val metaFile = afterPath.resolve("mocfsmetas/mocmetadata.json").toFile()
val allMeta: Map<String, Map<String, String>> = try {
    if (metaFile.exists()) gson.fromJson(metaFile.readText(), metaType) ?: emptyMap() else emptyMap()
} catch (_: Exception) { emptyMap() }
val filteredMeta = allMeta.filter { it.key in patchFilePaths }

// AFTER — afterFS is a session-start local, not kept; rebuild it over the
// session-private sandbox (static during the session, so a rescan is exact).
// Alternative: promote afterFS to a property — rejected, touches session lifecycle.
val filteredMeta = MocFileSystem(afterPath).effectiveMetadataFor(patchFilePaths)
```

### 6. `gui_web/.../web/Diffs.kt` — root-node rendering

```kotlin
// NEW in DiffTreeBuilder (optionDiffKind widened from private to internal)
fun buildTree(
    flatDiff: Map<String, OptionDiff>,
    resolveAction: (path: String) -> Pair<String?, String?>,
    unresolved: Set<String> = emptySet(),
    sourceMap: Map<String, String> = emptyMap()
): List<DiffNode> {
    val children = buildChildren(flatDiff, "$", resolveAction, unresolved, sourceMap)
    val rootDiff = flatDiff["$"] ?: return children   // no root record → unchanged behavior
    // Container summary (object on both sides) is not a root change of its own.
    if (rootDiff.newValue is Json5Object) return children
    // Atomic root replacement → buildNode applies the no-children rule itself.
    return listOf(buildNode(flatDiff, "$", resolveAction, unresolved, sourceMap))
}

// In buildNode — the atomic-replacement rule, at ANY depth:
val isAtomicReplacement = optDiff is OptionDiff.Changed && optDiff.newValue !is Json5Object
val children = if (isAtomicReplacement) emptyList()
               else buildChildren(flatDiff, path, resolveAction, unresolved, sourceMap)
```

Call sites swap `DiffTreeBuilder.buildChildren(flatDiff, "$", ...)` → `DiffTreeBuilder.buildTree(flatDiff, ...)`: `DiffRoutes.kt` (`GET /api/diff/{file}`, non-DELETED branch) and `RecompRoutes.kt` (`GET /api/recomp/diff/{file}`). `buildDeletedFileTree` unchanged.

### 7. Frontend (`diff.js`) — no change planned

The root node flows through the generic recursive `renderNode`: label `$`, kind badge, `old → new` values, action dropdown all come for free; depth-0 default-expansion applies. Task 3.2 verifies RAW toggle and stage/ignore actions on the root row; only if a glitch appears does `diff.js` get a minimal adjustment.

## Risks / Trade-offs

- [Draft finalization duplicated across `gui` and `gui_web`: the effective-type rule could drift between copies] → One task per copy with the same rule and mirrored tests; the shared `common` spec scenario ("Patch over an emptied file records the effective type") pins the contract.
- [A patch authored through the fallback records `content=text`; if the target instance later restores structured content, the file stays `text`-pinned there] → Same accepted one-way semantics as any text file; the future UI type override is the escape hatch.
- [Option-level entries of *older* patches targeting a currently-unreadable file still resurrect it as a JSON object at apply time (`getContent() ?: Json5Object()`)] → Pre-existing, unchanged by this fix; documented so the future "explicit errors" track can address it.
- [Ignores recorded on structured paths that are temporarily unreadable dangle without effect, then become meaningful again when the content recovers] → Arguably the desired behavior; value ignores still self-prune.
- [Recomposition sessions mixing structured entries with a fallback-shaped live diff may stage entries for paths absent from the live diff] → Same as today's handling of stale paths; overlap invariant is path-based and unaffected.

## Migration Plan

None. On-disk formats are unchanged. Existing patches apply identically (application uses patch-carried metadata; the fallback only affects reads of existing files).

## Open Questions

- Should the desktop's "value view when no sub-options" be aligned with C1's exact conditions in a later change, or left as-is? (No impact on this change's specs or tasks.)
