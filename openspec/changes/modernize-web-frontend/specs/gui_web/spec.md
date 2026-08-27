## MODIFIED Requirements

### Requirement: Browser client

The shipped SPA SHALL provide the complete authoring workflow as a bundled component-framework application embedded in the server jar (the jar stays self-contained; the build step is a development-time concern): a mode badge (NEW PATCH / AMEND / RECOMPOSITION) driven by session state, directory-grouped file tree, expandable diff tree with per-row and bulk stage/ignore actions, a staging panel with per-entry mode switching and source provenance, patch history with view/amend/recompose/delete (single and bulk), ignore management popovers, conflict-aware confirmation flows, and automatic refresh driven by the WebSocket event map with reconnect.

Interactive lists and trees (file tree, diff tree, staging panel, patch history) SHALL follow the WAI-ARIA tree view / listbox interaction model: roving focus with arrow-key navigation (left/right collapse/expand in trees), Home/End, character typeahead, Shift+Arrow selection extension, Shift/Ctrl+click multi-selection, and Ctrl/Cmd+A select-all. Row checking for bulk actions SHALL be a distinct state from focus/selection, with indeterminate state on parent rows.

Bulk actions (stage DEFAULT/OVERRIDE, IGNORE, RESET) SHALL be presented in an action bar associated with the panel, enabled by that panel's checked rows, rather than as always-visible controls on every row. Per-row actions SHALL be presented as a menu opened from a row affordance or context menu, keeping the state-aware model (UNSTAGED rendered empty / DEFAULTED / OVERRIDDEN / IGNORED, with RESET offered where applicable).

Multi-select gestures follow standard web conventions within a scope (same level, same parent). Select-all (Ctrl/Cmd+A) with an existing selection SHALL select every visible row in that selection's scope; with no selection, it SHALL select the root node's direct children — never the root node alone, so that the quickest gesture always means "every changed top-level option, individually" and not "the whole file".

#### Scenario: Amend entry points
- **WHEN** the user amends
- **THEN** they can start either from staged draft entries (finalize-for-amend then session) or directly from the last patch in history, and an unsaved-draft prompt offers Keep or Overwrite in the latter case

#### Scenario: Finalize gating in the UI
- **WHEN** a session has unresolved conflicts
- **THEN** the Finalize action is disabled until every conflict is staged or ignored

#### Scenario: Select-all without selection targets the root's children
- **WHEN** the user presses Ctrl/Cmd+A in the diff tree with no current selection
- **THEN** every visible direct child of the root node is selected, and the root node itself is not

#### Scenario: Select-all with selection keeps its scope
- **WHEN** the user presses Ctrl/Cmd+A in the diff tree with an existing selection
- **THEN** every visible row sharing the selection's scope (same level, same parent) is selected

#### Scenario: Keyboard navigation in trees
- **WHEN** a tree panel (file tree or diff tree) has focus
- **THEN** arrow keys move focus and collapse/expand branches, character keys jump to matching rows, Home/End jump to the first/last visible row, and Shift+Arrow extends the checked selection

#### Scenario: Bulk actions live in the action bar
- **WHEN** the user checks one or more rows in a panel
- **THEN** the panel's action bar offers the applicable bulk actions (DEFAULT, OVERRIDE, IGNORE, RESET) for the checked rows, and no per-row action buttons are permanently visible on unchecked rows

#### Scenario: Per-row actions via menu
- **WHEN** the user opens a row's action affordance or context menu
- **THEN** the state-aware action menu for that row opens (DEFAULT/OVERRIDE/IGNORE, plus RESET when the row is staged or ignored)
