# Tasks — fix-emptied-file-diff

## 1. Fix A — raw-content fallback in flat content (common)

- [x] 1.1 In `MocFile.getFlatContent`, fall back to `TextContentType`'s flat content (raw string at `$`) when the pinned type yields `null`, and cache the type actually used; expose it via `effectiveContentType()` (warm-on-demand); keep the `!exists` guard untouched; `ContentType.getFlatContent` keeps its original shape
- [x] 1.2 Add tests: pinned-`json` file empty / whitespace / invalid (`{oops`) / bare scalar (`42`, `null`, `"s"`) → flat content is `{ "$": <raw string> }`, `effectiveContentType()` is `text`, stored metadata unchanged; content valid again → structured flat content and `json` effective type recovered without intervention
- [x] 1.3 Add diff-level test: previously-JSON file emptied → per-file diff has a `Changed` record at `$` (new value `""`) plus `Deleted` children, no `""` record, file classified CHANGED; genuine file deletion still yields the `""` marker

## 2. Fix B — effective content type in patch metadata (gui_web + gui)

- [x] 2.1 Add `MocFileSystem.effectiveMetadataFor(filePaths)` in `common` (reads the cached `effectiveContentType()`, no re-inference) and use it in `gui_web` `DraftPatch` (`finalize`, `finalizeForAmend`) in place of the raw `mocmetadata.json` snapshot
- [x] 2.2 Use the same helper in the `gui` `DraftPatch` copy and in both `RecompositionDraft.finalize` copies (rebuilding `MocFileSystem(afterPath)` per the design sketch)
- [x] 2.3 Add round-trip test: finalize a patch staging `$` over an emptied pinned-`json` file → `mocmeta.json` records `content=text` → applying the patch on a fresh instance produces a genuinely empty file and an empty diff (no `""` literal, no residual CHANGED)

## 3. Fix C — conditional root-node rendering (gui_web)

- [x] 3.1 In `DiffTreeBuilder` (used by `GET /api/diff/{file}` and recomposition diff), when the flat diff contains a `$` record, return a root `DiffNode` (path/label `$`, kind, old/new values, staged/ignored annotations) wrapping the existing top-level children; otherwise keep current behavior
- [x] 3.2 Verify the SPA renders the root row correctly (label, kind badge, old → new values, action dropdown, stage/ignore actions) without JS changes, or make the minimal `diff.js` adjustment if needed
- [x] 3.3 Add route tests: emptied JSON file → one-node CHANGED root tree, staging `$` succeeds; root replaced by `[]` → root node visible; `$` without its own record → no synthetic root row

## 4. Verification

- [x] 4.1 Run full `common`, `gui_web`, and `gui` test suites
- [x] 4.2 Re-run the Playwright exploratory scenario (emptied JSON file end-to-end: file tree badge, root row `→ ""`, staging from the option tree, finalize, apply on a fresh instance) and confirm the matrix variants (whitespace / invalid / scalar / `[]` / `{}` / new empty file)
