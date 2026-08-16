# Delta: gui_web

## ADDED Requirements

### Requirement: State-aware action dropdown with RESET

The browser client SHALL model each per-row action dropdown as a state label plus a state-dependent menu of actions. The state SHALL be one of UNSTAGED (button rendered empty), DEFAULTED, OVERRIDDEN, or IGNORED, derived from the staged entry or matching ignore rule for that file/option. The menu SHALL offer:

- UNSTAGED: DEFAULT, OVERRIDE, IGNORE
- DEFAULTED or OVERRIDDEN: DEFAULT, OVERRIDE, IGNORE, RESET
- IGNORED: RESET

RESET SHALL return the option to UNSTAGED without confirmation: for a staged option it SHALL remove the staged entry (draft in NEW PATCH mode, recomposition entries in AMEND/RECOMPOSITION mode); for an ignored option it SHALL remove the matching ignore rule, using the rule's known kind — SESSION, VALUE, or PERMANENT via the general ignores endpoint, or the recomposition-scoped ignores endpoint for recomp ignores. RESET SHALL reuse existing API routes only.

#### Scenario: Unstage a staged option from the diff tree
- **WHEN** the user opens the dropdown of a DEFAULTED option and clicks RESET
- **THEN** the staged entry is removed without any confirmation dialog and the option returns to UNSTAGED

#### Scenario: Unignore an ignored option from the diff tree
- **WHEN** the user opens the dropdown of an IGNORED option
- **THEN** the menu offers only RESET, and clicking it removes the matching ignore rule without confirmation and the option returns to UNSTAGED

#### Scenario: RESET on a staged option in AMEND mode
- **WHEN** the user clicks RESET on an entry staged in an AMEND or RECOMPOSITION session (including entries originating from the amended patch)
- **THEN** the entry is removed via the recomposition entries endpoint without confirmation, with the same semantics as the staging panel remove button

#### Scenario: UNSTAGED button stays visually empty
- **WHEN** an option has no staged entry and no matching ignore rule
- **THEN** its dropdown button renders with no label text, as before

### Requirement: Staging clears recomposition-scoped ignores

When an option is staged (single or bulk) in an AMEND/RECOMPOSITION session, any recomposition-scoped ignore for that option SHALL be removed: the server SHALL remove it atomically as part of the stage operation so the invariant holds for any client, and the browser client SHALL additionally detect the recomp ignore when planning the stage and include the same "will be un-ignored" warning it shows for other ignore kinds. After such a stage, unstaging the option SHALL return it to UNSTAGED (never back to IGNORED).

#### Scenario: Stage over a recomp ignore with warning
- **WHEN** the user stages (or bulk-stages) an option that carries a recomposition-scoped ignore
- **THEN** the confirmation popup lists the un-ignore effect as for other kinds, and after confirming, both the staged entry exists and the recomp ignore is gone from the recomp-ignores list

#### Scenario: Server-side invariant
- **WHEN** a stage request reaches `POST /api/recomp/entries` for an option holding a recomp ignore (from any client, even one that did not remove the ignore itself)
- **THEN** the recomp ignore is removed atomically with the stage and an `ignores_changed` event is broadcast

#### Scenario: Unstage after stage-over-ignore returns to UNSTAGED
- **WHEN** the user stages an option that carried a recomp ignore and later unstages it (RESET or the staging panel remove button)
- **THEN** the option returns to UNSTAGED, not IGNORED

### Requirement: Bulk RESET action

The bulk action dropdowns (file tree, main diff area, staging panel) SHALL offer RESET in addition to DEFAULT, OVERRIDE, and IGNORE. Bulk RESET SHALL apply the per-row inverse across the whole selection — unstaging staged rows and unignoring ignored rows — without confirmation, reusing the same per-row semantics as the single-row RESET.

#### Scenario: Bulk reset a mixed selection
- **WHEN** the user selects multiple rows mixing staged and ignored options and picks RESET in the bulk dropdown
- **THEN** every staged row is unstaged and every ignored row is unignored, with no confirmation dialog

### Requirement: Directory-level ignore action

Directory rows in the file tree SHALL expose an ignore control that applies a DIRECTORY ignore for that directory path, with the same effects as the former popup-based DIRECTORY choice (ignored path registered, staged entries under the directory removed, filesystems reloaded). The ignore-type popup for file/option rows SHALL no longer offer the DIRECTORY kind. The file tree SHALL NOT offer a directory-level unignore control: an ignored directory has no visible diff rows and its ignore rule SHALL remain removable from the Ignores popover.

#### Scenario: Ignore a directory from its tree row
- **WHEN** the user clicks the ignore control on a directory row
- **THEN** a DIRECTORY ignore is applied for that directory and its files disappear from the diff tree

#### Scenario: Ignore-type popup kinds
- **WHEN** the user picks IGNORE on a file row or option row
- **THEN** the ignore-type popup offers SESSION, VALUE, and PERMANENT only — no DIRECTORY choice

### Requirement: Dropdown menu stays within its scroll container

When an action dropdown menu would overflow the bottom edge of its enclosing scroll container, the menu SHALL open upward from the button instead of downward, so that every option remains clickable. This SHALL apply to all action dropdowns (diff tree, file tree, staging panel, bulk bars).

#### Scenario: Single-option diff tree
- **WHEN** the user opens the dropdown on the only row of a diff tree (or any row near the bottom of a scroll container)
- **THEN** the full menu is visible and every option is clickable

#### Scenario: Menu with room below still opens downward
- **WHEN** the user opens a dropdown whose row has enough space below it within the scroll container
- **THEN** the menu opens downward as before
