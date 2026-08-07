# Proposal: server-side-staging-overlap-resolution

## Why

The draft stores have an implicit invariant — staged entries in the same file must never overlap (ancestor/descendant option paths) — but in gui_web it is enforced only by the SPA's frontend (`planStage`/`executeStage` in actions.js). The API endpoints (`POST /api/draft/entries`, `POST /api/recomp/entries`) upsert by exact key with no overlap handling. Any non-SPA client can silently create overlapping entries; two browser tabs can race past their local snapshots; and the frontend's multi-call remove-then-add sequence is non-atomic. The desktop gui enforces the same invariant in `AppState`, so the backends' permissiveness is a gap, not a design choice.

## What Changes

- Staging a draft entry (value or deletion) SHALL replace any already-staged entries it overlaps in the same file: staging a path removes staged ancestors and staged descendants, then upserts. Same for recomposition-session entries (including `sourceMap` cleanup for removed entries).
- Resolution lives in the web module's store mutators (`DraftPatch`, `RecompositionDraft`), not in the routes — every current and future caller gets the invariant. Routes and response contracts are unchanged (`200 {}`); existing WebSocket broadcasts propagate the resolution to all clients.
- The frontend needs no change: its confirmation dialog keeps warning before the call, and its pre-emptive removals become redundant-but-harmless (idempotent).
- Whole-file markers keep current semantics: `$` is an ancestor of all paths in its file; the file-deletion marker `""` overlaps nothing (unchanged — same as gui).
- No change to gui (frozen) or common.

**Non-goals**: changing the frontend's staging logic; adding a 409/force retry protocol; changing how staging interacts with ignores (an active ignore on a staged option stays — display already prefers the staged state).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: adds a server-side staging overlap invariant for draft and recomposition entries.

## Impact

- **Code**: `gui_web/.../web/DraftPatch.kt` and `RecompositionDraft.kt` mutators gain overlap removal; no route changes required.
- **API**: no contract change — behavior converges with what the SPA already produces.
- **Tests**: staging a child replaces a staged parent and vice versa (both endpoints); entries in other files untouched; exact-key upsert still updates mode in place; recomposition `sourceMap` provenance dropped for removed entries.
