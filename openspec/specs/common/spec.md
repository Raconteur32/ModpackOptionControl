# Common Specification

## Purpose

The `common` module is MOC's platform-independent core engine: it models managed config files and their formats, computes option-level diffs between file trees, stores and loads ordered patches, applies patches to a Minecraft instance with default/override semantics that respect user modifications, and provides the authoring-side machinery (draft patches, recomposition) used by both GUIs. All behavior hangs off a `PlatformService` singleton that supplies the game dir, config dir, and logging.

## Requirements

### Requirement: Platform service abstraction

The system SHALL obtain all platform-specific services through a globally installed `PlatformService` providing platform name, game directory, config directory, and three logging levels (info, error, critical). No common-module code may resolve paths or log by other means.

#### Scenario: Service must be installed before use
- **WHEN** any common singleton (file systems, patch list, drafts, settings) is touched before `PlatformService.INSTANCE` is assigned
- **THEN** initialization fails (lateinit access), making the missing installation explicit

#### Scenario: Path resolution
- **WHEN** common code needs the instance root or the config directory
- **THEN** it uses `PlatformService.getGameDir()` / `getConfigDir()` (config dir is `<gameDir>/config` in all current implementations)

### Requirement: Flattened option model

The system SHALL represent every managed file's content as a flat map from option path to value. Option paths use a JSONPath-like bracket notation rooted at `$`: object keys as `$['key']` (with `\` and `'` escaped), array indices as `$['arr'][0]`; the document root itself is the key `$`. Every node (objects, arrays, primitives) is present in the flat map, not only leaves. The conventions `optionPath = "$"` (whole-file value) and `optionPath = ""` (file-deletion marker) SHALL be reserved.

#### Scenario: Nested config flattens to paths
- **WHEN** a JSON file containing `{"a": {"b": 1}, "arr": [true]}` is flattened
- **THEN** the flat content contains keys `$`, `$['a']`, `$['a']['b']`, `$['arr']`, `$['arr'][0]`

### Requirement: Content type registry and detection

The system SHALL support pluggable content types identified by an id, registered in an ordered registry pre-populated with `json`, `properties`, and `toml`. A content type declares preferred extensions, validates content, reads/writes parsed content, and yields a confidence score (0 if invalid, otherwise 1, +2 if the extension matches). The `text` content type SHALL NOT be registered: it is the implicit fallback used when no registered type scores above 0. The file name `options.txt` SHALL always be treated as `properties` regardless of scoring.

#### Scenario: Type inference picks the best scoring type
- **WHEN** a file is loaded without stored content-type metadata
- **THEN** the registered type with the highest confidence score wins, or `text` if all score 0

#### Scenario: Blank unknown file falls back to text
- **WHEN** a blank file with an unknown extension is inspected
- **THEN** its content type is `text` (structured types reject blank content)

#### Scenario: Metadata extraction is guarded
- **WHEN** format-specific metadata extraction for a file hangs or crashes
- **THEN** after a 1-second timeout the file is treated as invalid content for that type

### Requirement: Format-faithful writing

When the system writes a file, it SHALL preserve the format conventions recorded in the file's metadata:

- **properties**: original separator (first unescaped `=`, `:`, or whitespace), original key order (new keys appended), and the `version` key pinned to the first line; object/array values are serialized as single-line JSON5 strings.
- **toml**: tables recorded as inline in metadata (`inline_tables`) are written inline; others are written as sections; the root must be an object.
- **text**: the content round-trips byte-exactly as a single string.
- **json**: parsed with a lenient JSON5 reader (single quotes, unquoted keys, comments, NaN/Infinity) and written pretty-printed (formatting is not preserved).

#### Scenario: Properties round-trip
- **WHEN** a properties file with `:` separators and a specific key order is modified and saved
- **THEN** untouched lines keep their separator and relative order, and `version` remains first

#### Scenario: TOML inline tables preserved
- **WHEN** a TOML file containing `point = {x = 1, y = 2}` and a `[database]` section is modified elsewhere and saved
- **THEN** `point` remains inline and `database` remains a section

### Requirement: Number fidelity

JSON5 numbers SHALL retain their exact textual representation through parsing, diffing, drafting, patch serialization, and application. Number equality is textual: `54` vs `54.0` and `0xFF` vs `255` are changes; big-integer/big-decimal precision is preserved end to end.

#### Scenario: Representation change detected
- **WHEN** an option changes from `54` to `54.0`
- **THEN** the diff reports a Changed entry for that option

### Requirement: Option-level diffing

The system SHALL compute the diff to transform one file's flat content into another's, producing `New`, `Deleted`, and `Changed` option records. Rules: missing file target yields a single `Deleted` record at path `""`; arrays are diffed atomically (no per-element records); container `Changed` records exist only when they have leaf changes beneath them; and after rationalization no records may exist underneath a `Deleted` path. File-system diffs classify each path as NEW, DELETED, or CHANGED (identical files produce no entry).

#### Scenario: Deleted subtree rationalized
- **WHEN** an object and all its children are removed
- **THEN** the diff contains the parent's Deleted record and no records for its descendants

#### Scenario: Array treated atomically
- **WHEN** one element of an array changes
- **THEN** the diff records the change at the array's path, not per element

### Requirement: Managed file-system scanning

`MocFileSystem` SHALL scan its root for regular text files, excluding its own metadata directory (`mocfsmetas`), every configured ignored path, and binary files (null-byte and UTF-16 heuristics). Each scanned file's encoding and content type are detected on first load and persisted in `mocfsmetas/mocmetadata.json`.

#### Scenario: Ignored path excluded
- **WHEN** a file's path starts with `<root>/<ignoredPath>`
- **THEN** it never appears in the scan (applies to directories and files)

### Requirement: Reference file system

When constructed with a reference (`hasRef`), the system SHALL maintain `mocfsmetas/ref` as the pure patched state: it is deleted and rebuilt by replaying every already-applied patch in recorded order with forced override, and it is kept in sync on every subsequent patch application. "The user modified option X" is defined as "X appears in the diff of the live file system against the ref".

#### Scenario: Ref rebuilt on construction
- **WHEN** a file system with `hasRef` is constructed over an instance with recorded applied patches
- **THEN** any existing ref directory is wiped and rebuilt from the applied-patch record alone

### Requirement: Patch application semantics

Applying a patch SHALL respect per-entry mode and kind:

- `OVERRIDE` entries always apply.
- `DEFAULT` value entries apply only if the option does not exist locally, or the local value still matches the ref (user has not touched it), or application is forced.
- `DEFAULT` deletion entries apply only if the local value still matches the ref, or application is forced; consequently they never apply on a file system without a ref.
- Granularity is per option: user edits to sibling options in the same file do not block a default on an untouched option.

Application order within a patch: whole-file deletions first, then content entries grouped by file, applied in patch order, creating missing intermediate objects as needed. Patch metadata is merged into the target's `mocmetadata.json`.

#### Scenario: User-modified option survives a default
- **WHEN** a DEFAULT value entry targets an option the user changed relative to ref
- **THEN** the user's value is kept

#### Scenario: Untouched option receives the default
- **WHEN** a DEFAULT value entry targets an option whose local value still matches ref (or is absent)
- **THEN** the entry's value is written

#### Scenario: Default deletion skipped without ref
- **WHEN** a patch with a DEFAULT deletion entry is applied to a file system without a ref
- **THEN** the deletion is not applied, though the patch is still recorded as applied

### Requirement: Patch recording and pending application

Each successful patch application SHALL be recorded in `mocfsmetas/mocappliedpatches.json` (name + ISO instant; legacy name-only lists are migrated on load with an empty datetime) and logged to `mocfsmetas/mocappliedlogs/applied.<patchName>.json5` as a list of `{kind, file, path, from, to}` records. Applying pending patches SHALL process every patch in `PatchList` order that is not yet recorded, and SHALL stop at the first failing patch so later patches never apply after a failure.

#### Scenario: Skipped modpack versions catch up
- **WHEN** a user jumps from modpack v1.0 to v3.0
- **THEN** the v2.0 and v3.0 patches both apply, in list order, on next launch

#### Scenario: Failure aborts the remainder
- **WHEN** a patch fails during pending application
- **THEN** the error is reported and no subsequent patch is attempted

### Requirement: Patch storage format

A patch SHALL be stored at `config/moc/patches/<name>/` containing:

- `patch.json` — a JSON5 array of entries `{file_path, option_path, from_value, to_value, kind: VALUE|DELETION, mode: OVERRIDE|DEFAULT}`.
- `mocmeta.json` — per-file metadata maps (content type, encoding, separator, key order, inline tables) sufficient to recreate touched files faithfully.

The patch directory path SHALL be defined by a single shared constant; all producers and consumers (patch loading, patch list folder management, draft finalization, recomposition staging) SHALL resolve it through that constant. Installs with a legacy `config/moc/patchs/` directory are migrated per the migration requirement; no fallback read of the legacy location is performed after migration.

Patches SHALL also load from a zip containing the same two flat entries, with the patch name defaulting to the zip basename. All loaders are tolerant: missing or malformed files yield empty entries/metadata rather than errors, and malformed individual entries are skipped.

#### Scenario: Malformed patch entry skipped
- **WHEN** `patch.json` contains an entry with a missing or invalid field
- **THEN** that entry is skipped and the remaining valid entries load

#### Scenario: Single source for the patch directory path
- **WHEN** any component resolves a patch directory
- **THEN** it uses the shared constant, so the location cannot drift between readers and writers

### Requirement: Ordered patch list with deletion propagation

The system SHALL maintain `config/moc/patch-list.json` as the ordered list of patch names; list order is application order and new patches append. Deleting a patch removes it from the active list (preserving remaining order), records its name in `config/moc/deleted-patch-list.json`, and deletes its folder. On startup, folders of names on the deleted list are removed, propagating deletions to clients. Re-adding a name clears it from the deleted list.

#### Scenario: Deletion propagates to clients
- **WHEN** a client still has the folder of a patch that was deleted upstream
- **THEN** startup cleanup deletes the orphan folder

### Requirement: Settings and ignored paths

The system SHALL persist settings in `config/moc.json`, auto-created on first access with default ignored paths `mods`, `resourcepacks`, `logs`, `config/moc.json`, `config/moc`, `mocfsmetas`. Adding or removing an ignored path takes effect in place for the live file-system singletons and persists immediately.

#### Scenario: MOC never scans itself
- **WHEN** a fresh instance is scanned
- **THEN** mod jars, MOC's own config/state, and metadata directories are excluded by default

### Requirement: Non-destructive migration

The system SHALL migrate legacy on-disk layouts (dot-prefixed and flat metadata files, flat applied-patch logs, `.mocmeta.json`, and a legacy `config/moc/patchs/` patch directory) to the current layout for both the instance and the dev ref, idempotently: missing sources are skipped and existing destinations are never overwritten. The legacy patch directory is renamed to `config/moc/patches/` only when the destination does not already exist.

#### Scenario: Migration on empty install
- **WHEN** migration runs on an install with no legacy files
- **THEN** it completes without error and changes nothing

#### Scenario: Legacy patch directory renamed
- **WHEN** an install has `config/moc/patchs/` and no `config/moc/patches/`
- **THEN** migration renames the directory with all patch folders intact, and a second migration run is a no-op

#### Scenario: Both directories present
- **WHEN** an install has both `config/moc/patchs/` and `config/moc/patches/`
- **THEN** migration leaves both untouched (the current location wins; nothing is overwritten)
