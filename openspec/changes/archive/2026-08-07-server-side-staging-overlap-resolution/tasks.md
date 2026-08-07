# Tasks: server-side-staging-overlap-resolution

## 1. Store mutators

- [x] 1.1 `DraftPatch` (web copy): in `setValueEntry`/`setDeletionEntry`, remove staged entries in the same file whose option path is an ancestor or descendant of the new entry (via `isDescendant`), before the exact-key upsert; persist once
- [x] 1.2 `RecompositionDraft` (web copy): same overlap removal in its `setValueEntry`/`setDeletionEntry`, also dropping `sourceMap` provenance for removed entries

## 2. Tests

- [x] 2.1 Draft: staging `$['a']['b']` after `$['a']` leaves only the child entry; staging `$['a']` after `$['a']['b']`+`$['a']['c']` leaves only the parent
- [x] 2.2 Draft: entry in another file untouched; exact-key restage updates mode in place
- [x] 2.3 Draft: file-deletion marker `""` coexists with value entries in the same file
- [x] 2.4 Recomp: overlap replacement works via `POST /api/recomp/entries` and removes the overlapped entry's `sourceMap` provenance
- [x] 2.5 Route-level test: resolution happens through `POST /api/draft/entries` alone (no frontend pre-removal), proving the invariant is server-side

## 3. Verification

- [x] 3.1 `gradle :gui_web:test` green
- [x] 3.2 `openspec validate server-side-staging-overlap-resolution` passes
