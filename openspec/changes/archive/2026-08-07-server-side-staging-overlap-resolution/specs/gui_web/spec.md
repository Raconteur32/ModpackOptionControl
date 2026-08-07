# Web GUI Delta

## ADDED Requirements

### Requirement: Server-side staging overlap invariant

The server SHALL maintain a no-overlap invariant on staged entries: within the same file, two staged entries may not have equal-or-nested option paths (except the file-deletion marker `""`, which overlaps nothing). Staging an entry SHALL atomically remove any already-staged entries whose option path is an ancestor or descendant of the new entry's path, then upsert the new entry. This applies to both the draft (`POST /api/draft/entries`) and recomposition sessions (`POST /api/recomp/entries`), and SHALL hold regardless of which client performs the calls.

#### Scenario: Staging a child replaces the staged parent
- **WHEN** `$['a']` is staged and a client stages `$['a']['b']` via the API
- **THEN** the staged set contains only the new entry for `$['a']['b']`

#### Scenario: Staging a parent replaces staged descendants
- **WHEN** `$['a']['b']` and `$['a']['c']` are staged and a client stages `$['a']` via the API
- **THEN** the staged set contains only the new entry for `$['a']`

#### Scenario: Entries in other files are untouched
- **WHEN** an entry in `a.json` is staged and a client stages an entry in `b.json`
- **THEN** the `a.json` entry remains staged

#### Scenario: Exact-key restaging updates in place
- **WHEN** an option is staged and a client stages the same option path with a different mode
- **THEN** the entry is updated in place (no removal side effects)

#### Scenario: File deletion marker coexists with value entries
- **WHEN** a file-deletion entry (`""`) is staged for a file and a client stages a value entry in the same file
- **THEN** both entries remain staged (deletion-then-recreate semantics are preserved)

#### Scenario: Recomposition provenance follows removed entries
- **WHEN** a recomposition staging removes an auto-populated entry as an overlapped ancestor or descendant
- **THEN** the removed entry's source-patch provenance is discarded with it
