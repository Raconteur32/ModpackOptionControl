# Proposal: dropdown-reset-action

## Why

The web GUI action dropdown has three UX gaps:

1. **No way back**: once an option is staged (DEFAULT/OVERRIDE), the only way to unstage it is the ✕ button in the staging panel; once ignored, the only way is the Ignores popover. The dropdown itself — the control the user just used — offers no inverse action.
2. **Clipped menu**: the dropdown menu opens downward inside scroll containers (`overflow-y: auto`), so rows near the bottom of a panel (e.g. a diff tree with a single option) have their menu truncated and unusable.
3. **Misplaced directory ignore**: the DIRECTORY ignore kind is a radio inside the per-option ignore-type popup, although it targets a whole directory and has no per-option semantics. Directories themselves have no action control at all.

## What Changes

- **State/action dropdown model**: the button label reflects the option's *state* (`UNSTAGED` shown empty — visual status quo —, `DEFAULTED`/`OVERRIDDEN` shown as today, `IGNORED`), while the menu offers *actions*. Menus become state-aware:
  - `UNSTAGED` → DEFAULT · OVERRIDE · IGNORE (as today)
  - `DEFAULTED`/`OVERRIDDEN` → DEFAULT · OVERRIDE · IGNORE · **RESET**
  - `IGNORED` → **RESET**
- **RESET**: unified inverse action returning the option to `UNSTAGED` — removes the draft/recomp entry when staged, removes the ignore rule when ignored (SESSION/VALUE/PERMANENT via `DELETE /api/ignores`, recomp-scoped via `DELETE /api/ignores/recomp`). No confirmation, mirroring the staging panel ✕ semantics. Requires no new endpoint.
- **Bulk RESET**: the bulk action dropdown gains a RESET option that applies the per-row inverse across the selection (unstage staged rows, unignore ignored rows).
- **Directory ignore relocation**: the DIRECTORY radio is removed from the ignore-type popup; directory rows in the file tree gain an ignore button that applies a directory ignore for that path. No per-directory unignore in the tree (an ignored directory disappears from the diff entirely; removal stays in the Ignores popover).
- **Dropdown flip**: when the menu would overflow the bottom of its scroll container, it opens upward instead. Applied globally to all action dropdowns (diff tree, file tree, staging panel).
- **Recomp-ignore cleanup on stage** (bug fix surfaced by RESET): staging an option in an AMEND/RECOMPOSITION session currently leaves any recomposition-scoped ignore for that option in place (the client conflict planner is blind to `recompIgnores`, and the server never removes it), so unstaging later makes the option flip back to IGNORED. The server SHALL remove the recomp ignore atomically when the option is staged, and the client SHALL surface the "will be un-ignored" warning for recomp ignores like it does for the other kinds.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: new requirements for the state/action dropdown model (RESET, state-aware menus, bulk RESET), directory-row ignore action, ignore-type popup without the DIRECTORY radio, and dropdown menu clipping avoidance.

## Impact

- **Code**: `gui_web/src/main/resources/static/` — `js/dropdown.js`, `js/actions.js`, `js/diff.js`, `js/filetree.js`, `js/staging.js`, `js/ignores.js`, `css/components.css`; plus one server-side hardening in `routing/RecompRoutes.kt` (recomp-ignore removal on stage). No new API route.
- **Desktop GUI** (`gui` module, Compose): out of scope — it has its own UI; no shared-state impact (RESET only calls existing mutations).
- **Specs**: `openspec/specs/gui_web/spec.md` gains requirements; no existing requirement text is contradicted.
- **Tests**: JS vitest suite where applicable; a ktor test for the recomp-ignore removal on stage (alongside existing `RecompRoutesTest`/`IgnoreRoutesTest` coverage).
