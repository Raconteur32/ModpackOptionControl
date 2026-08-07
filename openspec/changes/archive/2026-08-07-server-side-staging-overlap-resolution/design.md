# Design: server-side-staging-overlap-resolution

## Context

See proposal.md — the invariant exists only in the SPA (`planStage`/`executeStage`), the API upserts blindly. gui enforces the same rule in `AppState` after a confirmation dialog.

## Goals / Non-Goals

**Goals:** the invariant holds for any API client, atomically per call; user-facing behavior unchanged.

**Non-Goals:** frontend changes; 409/force protocol; ignore-staging interaction; gui/common changes; rethinking the `""` file-deletion semantics (it deliberately overlaps nothing — "delete then recreate" is a legitimate composition).

## Decisions

### D1: Automatic resolution, not a 409/force protocol

Staging replaces overlappers atomically, mirroring gui's post-confirmation outcome. The SPA still warns first via `planStage`, so users keep the confirmation UX. **Alternative considered:** return `409 {overlaps: [...]}` and require a `force` flag — more explicit for hypothetical third-party clients, but it changes the contract, complicates the frontend, and buys nothing the confirmation dialog doesn't already provide. Rejected as speculative generality; can be added later without breaking anything (resolution today = what force would do).

### D2: In the store mutators, not the routes

`DraftPatch.setValueEntry`/`setDeletionEntry` and the `RecompositionDraft` equivalents remove overlappers (same file, `isDescendant` in either direction) before upserting. Rationale: routes stay thin, future callers inherit the invariant, and the logic sits next to the exact-key upsert it extends. `RecompositionDraft` also drops `sourceMap` provenance for removed entries (consistent with its manual-staging behavior). Auto-population is unaffected: conflicting (overlapping) entries are already excluded before staging, so the new code path is inert there. **Alternative:** a route-layer helper shared by DraftRoutes/RecompRoutes — works, but leaves the stores unsafe for any other caller (tests, future endpoints).

### D3: Frontend untouched

`planStage`'s removals become redundant (idempotent DELETEs against an already-resolved store). Simplifying the frontend is a separate, optional cleanup — not required for correctness since the server now guarantees the end state either way.

## Risks / Trade-offs

- [The two store copies (gui frozen, web living) now diverge behaviorally for the first time] → Accepted: this is the declared direction (web is the living implementation); the on-disk format is unchanged so state compatibility holds.
- [A non-SPA client loses the "confirm before replace" step — replacement is silent] → Same end state gui produces after its confirmation; removals are visible via `draft_changed` broadcasts and the staged list. Documented in the spec scenarios.
