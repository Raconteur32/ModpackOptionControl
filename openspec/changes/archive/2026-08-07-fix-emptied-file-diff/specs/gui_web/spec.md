# gui_web delta — fix-emptied-file-diff

## MODIFIED Requirements

### Requirement: Diff exposure

Diff endpoints SHALL serve the diff between the live instance and the dev reference as file summaries (ordered CHANGED, then NEW, then DELETED, alphabetical within group) and per-file option trees with labels, kinds, old/new values, staged/ignored annotations, and (in sessions) conflict and source-patch provenance. Before every diff response, stale value ignores are pruned. With `showAll=true`, the diff is computed against an empty file system so every option appears as NEW. Deleted files appear as a single node labelled `(file)`.

A per-file option tree SHALL include the root node `$` itself — as a regular node carrying its label, kind, and old/new values — whenever the root was replaced atomically, i.e. the new root value is not an object (a scalar, an array, or a raw/empty string). A container-summary record at `$` (root object on both old and new sides, with changes only beneath it) carries no information of its own and SHALL NOT produce a synthetic root row; the tree then exposes only the children of `$`. A file whose only visible change is such an atomic root replacement SHALL therefore still display a one-node tree instead of an empty tree.

#### Scenario: Staged annotation wins
- **WHEN** an option is both staged and ignored
- **THEN** the node reports the staged action (DEFAULT/OVERRIDE)

#### Scenario: Emptied JSON file shows a root change
- **WHEN** a managed JSON file whose previous content was an object is now empty
- **THEN** the per-file diff tree shows a single CHANGED root node whose new value is `""`, and that node can be staged like any other option

#### Scenario: Root replaced by an array is visible
- **WHEN** a managed JSON file's root changed from an object to an array (including `[]`)
- **THEN** the per-file diff tree shows the CHANGED root node with its old and new values

#### Scenario: Container-summary root stays implicit
- **WHEN** a file's root is an object on both sides of the diff and only options below `$` changed
- **THEN** the tree's top level lists those options directly, with no synthetic root row

#### Scenario: Atomic replacement hides former children at any depth
- **WHEN** any option node (including the root) changes from an object to a non-object value — e.g. `{"test": {"test": "test"}}` becoming `{"test": "test"}`
- **THEN** the tree shows only the CHANGED node with its old and new values; the former children's Deleted records remain in the underlying diff (they drive apply-time matching) but are not displayed
