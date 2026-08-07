# GUI Specification

## Purpose

The `gui` module is MOC's desktop authoring tool (Compose for Desktop) for modpack makers. It shows the live diff between a Minecraft instance and the reference state produced by the existing patches, and lets the author stage changes into draft patches, finalize/amend/delete patches, recompose ranges of patches, and manage ignore rules — operating on the same on-disk state under `config/moc/` that the mod consumes.

## Requirements

### Requirement: Startup sequence and game-dir resolution

On startup the app SHALL install its platform service, run migrations, delete any stale `moc/dev-ref` tree, run patch-list startup cleanup, prune redundant ignore entries, apply pending patches, and regenerate the dev reference tree before showing the window. The game directory resolves from (in order) a CLI argument, the `moc.gameDir` system property, or auto-detection among `./`, `../`, `run/`, `../fabric/run/`, `../run/`; a directory is valid only if it contains both `config/` and `mods/`. An invalid explicit directory or no candidate SHALL exit with code 1 and an error message.

#### Scenario: Explicit invalid directory
- **WHEN** the app is launched with a game-dir argument lacking `config/` or `mods/`
- **THEN** it prints an error and exits with code 1

### Requirement: Diff browsing

The app SHALL present the diff between the live instance and the dev reference as a changed-file list (sorted, kind-tagged new/deleted/changed), drill-down into a hierarchical option tree per file, and a value view showing old/new values (side-by-side for changes). Entries fully covered by ignores are hidden. A raw/rendered toggle unquotes strings and expands escapes.

#### Scenario: Navigation stack
- **WHEN** the user opens a changed file
- **THEN** they enter the option tree at root `$` (or the value view directly if the file has no sub-options) and can navigate back level by level

### Requirement: Draft staging with overlap protection

The user SHALL be able to stage any file, option subtree, or single value into the draft as DEFAULT or OVERRIDE, and unstage at any level or from the draft panel. Staging an entry that overlaps already-staged ancestors or descendants SHALL prompt for confirmation naming what will be removed; confirming replaces the overlapped entries, cancelling aborts. Staged state is visibly badged in all views.

#### Scenario: Overlap prompt
- **WHEN** the user stages an option whose parent is already staged
- **THEN** a confirmation dialog states the parent entry will be removed before proceeding

### Requirement: Patch finalization

Finalizing a non-empty draft SHALL prompt for a patch name with live uniqueness validation (blank or taken names block confirmation). Confirmation creates the patch, appends it to the patch list, applies it to the dev reference, clears the draft, and expires session ignores. When at least one patch exists, the dialog also offers **Amend**, which stashes the draft into the amend directory and enters amend mode over the last patch.

#### Scenario: Name collision blocked
- **WHEN** the user types the name of an existing patch
- **THEN** an error is shown and confirmation is disabled

### Requirement: Patch browsing and deletion

The Patches tab SHALL list patches in application order with view, delete, and recompose actions, supporting range selection via shift-click. Deletion requires confirmation explaining that the patch is removed from the list, its folder deleted, and its name recorded so the deletion propagates to all users.

#### Scenario: Patch content view
- **WHEN** the user views a patch
- **THEN** its entries are listed with file path, option path, and mode badges (or a load-failure message)

### Requirement: Amend workflow

Amend mode SHALL edit exactly the last patch through the recomposition editor (shown in the New Patch tab), seeded with the stashed amend entries applied on top. Amend finalization allows reusing the last patch's own name but rejects other existing names, replaces the last patch, and exits amend mode. An active amend session persists across restarts and is restored on launch.

#### Scenario: Amend keeps the name
- **WHEN** the user finalizes an amend reusing the amended patch's name
- **THEN** the last patch is replaced in place, keeping its position in the list

### Requirement: Recomposition workflow

The user SHALL be able to start a recomposition over a selected contiguous range of patches. If a recomposition or amend session is already active, launching another SHALL prompt to resume the existing session, overwrite it with the new range, or cancel. The editor mirrors the draft workflow over the range's net diff, with the header showing the range and patch names; finalization replaces the range with one patch (allowing reuse of a name within the range) and cancellation discards the session. Session state persists across restarts and reopens automatically.

#### Scenario: Conflict on launch
- **WHEN** the user requests a new recomposition while one is in progress
- **THEN** a dialog offers Resume existing / Overwrite with new range / Cancel

### Requirement: Ignore rules

The app SHALL support four ignore kinds, chosen per entry via a dialog: **session** (expires when a patch is finalized or amend stashed), **value** (suppresses only while the live new value equals the recorded target; stale entries are auto-pruned on diff load), **permanent**, and **directory** (written to `ignored_paths` in `moc.json`, reloading both file systems; offered with an editable path from the file list). Ignored entries disappear from diff views, including collapsed subtrees. An ignore panel provides filtering by kind, search, counts, and per-entry removal. Recomposition sessions have their own ignore list, added instantly without a dialog, persisted with the session, and cleared on finalize or cancel.

#### Scenario: Value ignore expires
- **WHEN** an option ignored "until value changes" takes on a different value
- **THEN** the ignore is auto-removed and the change becomes visible again

#### Scenario: Directory ignore
- **WHEN** the user ignores a directory permanently
- **THEN** the path is added to `ignored_paths` in `moc.json`, both file systems reload, and matching entries vanish from the diff

### Requirement: Keyboard-driven three-panel layout

The New Patch tab SHALL present resizable Changes / Draft / Ignores panels with visible focus indication, Tab cycling focus, and full keyboard control: arrows navigate, Enter opens, Escape/left goes back, `D` stages default, `O` stages override, `R` removes, `I` ignores, `F` finalizes, `T` toggles raw values, Delete removes a selected patch. All mouse actions have keyboard equivalents.

#### Scenario: Full workflow without a mouse
- **WHEN** the user browses the diff, stages entries, and finalizes using only the keyboard
- **THEN** every step of the draft-to-patch workflow is reachable
### Requirement: Embedded authoring engine with shared-state compatibility

The desktop GUI SHALL embed its own implementation of the patch-authoring engine (draft staging, recomposition, dev reference tree) rather than relying on the core runtime module to provide it. Its behavior SHALL remain identical to before the extraction, and it SHALL keep reading and writing the established on-disk authoring state formats and locations so that it remains interchangeable with the web GUI on the same instance.

#### Scenario: State interchangeable with the web GUI
- **WHEN** a draft or recomposition session is created with the desktop GUI and the web GUI is later opened on the same instance
- **THEN** the web GUI restores that draft or session exactly, and vice versa

#### Scenario: Unconditional dev-ref regeneration preserved
- **WHEN** the desktop GUI starts
- **THEN** it regenerates the dev reference tree unconditionally, as before this change
