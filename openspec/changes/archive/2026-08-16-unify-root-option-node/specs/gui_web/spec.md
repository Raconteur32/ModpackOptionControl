## MODIFIED Requirements

### Requirement: Diff exposure

Diff endpoints SHALL serve the diff between the live instance and the dev reference as file summaries (ordered CHANGED, then NEW, then DELETED, alphabetical within group) and per-file option trees with labels, kinds, old/new values, staged/ignored annotations, and (in sessions) conflict and source-patch provenance. Before every diff response, stale value ignores are pruned. With `showAll=true`, the diff is computed against an empty file system so every option appears as NEW.

Every per-file option tree SHALL be rooted at a single root node: the `$` node for an existing file, or the `(file)` deletion-marker node (`""`) for a deleted file. The root node is a regular node — it carries its label, kind, staged/ignored annotations, and (when leaf) old/new values, and it SHALL be stageable, ignorable and resettable through the same per-row and bulk machinery as any other option. Staging the root node stages the whole file content (its container value), which is semantically distinct from staging its children individually (only the changed, displayed options).

A node that has children SHALL NOT carry serialized old/new values in the tree payload, and the client SHALL NOT render values for such a node. A leaf node — including an atomic replacement, whose former children's Deleted records remain in the underlying flat diff (they drive apply-time matching) but are not displayed — SHALL carry and render its old/new values. This applies uniformly to every node, including the root: the container-summary record at `$` (root object on both sides, changes only beneath it) produces a root node with children and no values.

#### Scenario: Staged annotation wins
- **WHEN** an option is both staged and ignored
- **THEN** the node reports the staged action (DEFAULT/OVERRIDE)

#### Scenario: Root node always present
- **WHEN** a file's root is an object on both sides of the diff and only options below `$` changed
- **THEN** the tree's top level is the CHANGED root node `$` itself, carrying no old/new values, with the changed options as its children

#### Scenario: Container-summary root stays implicit
- **WHEN** a file's root is an object on both sides of the diff and only options below `$` changed
- **THEN** the container-summary record at `$` produces a root node with children and no old/new values (the root node is structural; it exposes no container value of its own)

#### Scenario: Emptied JSON file shows a root change
- **WHEN** a managed JSON file whose previous content was an object is now empty
- **THEN** the per-file diff tree shows a single CHANGED root node whose new value is `""`, and that node can be staged like any other option

#### Scenario: Root replaced by an array is visible
- **WHEN** a managed JSON file's root changed from an object to an array (including `[]`)
- **THEN** the per-file diff tree shows the CHANGED root node with its old and new values and no children

#### Scenario: Atomic replacement hides former children at any depth
- **WHEN** any option node (including the root) changes from an object to a non-object value — e.g. `{"test": {"test": "test"}}` becoming `{"test": "test"}`
- **THEN** the tree shows only the CHANGED node with its old and new values; the former children's Deleted records remain in the underlying diff (they drive apply-time matching) but are not displayed

#### Scenario: Deleted file shows a single (file) root node
- **WHEN** a managed file no longer exists
- **THEN** the per-file diff tree is a single DELETED root node labelled `(file)`, carrying its former value, stageable and ignorable like any other node

#### Scenario: Staging the root stages the whole file
- **WHEN** the user stages the root node `$` of a changed file
- **THEN** the resulting patch entry captures the entire file content at `$` — including unchanged and ignored options — rather than only the options visible in the tree

#### Scenario: Node with children carries no values
- **WHEN** a diff node has children (e.g. the container-summary root of a large config file)
- **THEN** the diff response does not serialize that node's old/new values and the client renders no value column for it

### Requirement: Browser client

The shipped SPA SHALL provide the complete authoring workflow without a build step: a mode badge (NEW PATCH / AMEND / RECOMPOSITION) driven by session state, directory-grouped file tree, expandable diff tree with per-row and bulk stage/ignore actions, a staging panel with per-entry mode switching and source provenance, patch history with view/amend/recompose/delete (single and bulk), ignore management popovers, conflict-aware confirmation flows, and automatic refresh driven by the WebSocket event map with reconnect.

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
