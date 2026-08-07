# common delta — fix-emptied-file-diff

## MODIFIED Requirements

### Requirement: Flattened option model

The system SHALL represent every managed file's content as a flat map from option path to value. Option paths use a JSONPath-like bracket notation rooted at `$`: object keys as `$['key']` (with `\` and `'` escaped), array indices as `$['arr'][0]`; the document root itself is the key `$`. Every node (objects, arrays, primitives) is present in the flat map, not only leaves. The conventions `optionPath = "$"` (whole-file value) and `optionPath = ""` (file-deletion marker) SHALL be reserved.

When an existing file's content cannot be parsed under its effective content type (empty or blank content, invalid syntax, or a bare scalar root), the flat content SHALL fall back to the `text` reading: a single entry mapping `$` to the raw string content. The fallback is computed at read time and SHALL NOT modify the file's stored content-type metadata, so the file transparently recovers its structured type when its content parses again.

#### Scenario: Nested config flattens to paths
- **WHEN** a JSON file containing `{"a": {"b": 1}, "arr": [true]}` is flattened
- **THEN** the flat content contains keys `$`, `$['a']`, `$['a']['b']`, `$['arr']`, `$['arr'][0]`

#### Scenario: Unparsable content falls back to raw string at root
- **WHEN** a file pinned as `json` is empty, blank, syntactically invalid, or reduced to a bare scalar (`42`, `null`, `"s"`)
- **THEN** its flat content is `{ "$": <raw file content as string> }` and its stored metadata still pins `json`

#### Scenario: Structured type recovers automatically
- **WHEN** a file previously read through the fallback contains valid structured content again
- **THEN** its flat content is computed under the pinned type with no manual intervention

### Requirement: Option-level diffing

The system SHALL compute the diff to transform one file's flat content into another's, producing `New`, `Deleted`, and `Changed` option records. Rules: a missing target file yields a single `Deleted` record at path `""`; arrays are diffed atomically (no per-element records); container `Changed` records exist only when they have leaf changes beneath them; and after rationalization no records may exist underneath a `Deleted` path. File-system diffs classify each path as NEW, DELETED, or CHANGED (identical files produce no entry).

The `""` file-deletion marker SHALL be produced only when the target file does not exist. Because the raw-content fallback guarantees every existing file is readable at least as `text`, a file that exists but cannot be parsed under its pinned type SHALL diff through the root path `$` (e.g. a `Changed` record from the old root value to the raw string content), never through the `""` marker.

#### Scenario: Deleted subtree rationalized
- **WHEN** an object and all its children are removed
- **THEN** the diff contains the parent's Deleted record and no records for its descendants

#### Scenario: Array treated atomically
- **WHEN** one element of an array changes
- **THEN** the diff records the change at the array's path, not per element

#### Scenario: Emptied file diffs at the root, not as a deletion
- **WHEN** a managed file that previously contained `{"a": 1, "b": {"c": "x"}}` is now empty
- **THEN** the per-file diff contains a `Changed` record at `$` whose new value is the empty string, `Deleted` records for the former child options, and no `""` record — and the file is classified CHANGED, not DELETED

### Requirement: Patch storage format

A patch SHALL be stored at `config/moc/patches/<name>/` containing:

- `patch.json` — a JSON5 array of entries `{file_path, option_path, from_value, to_value, kind: VALUE|DELETION, mode: OVERRIDE|DEFAULT}`.
- `mocmeta.json` — per-file metadata maps (content type, encoding, separator, key order, inline tables) sufficient to recreate touched files faithfully.

The content type recorded in `mocmeta.json` for a touched file SHALL be the effective type under which its values were captured: when the file's stored type could not parse its content at authoring time (raw-content fallback), the recorded type SHALL be `text`. This guarantees round-trip fidelity — a patch that captures an emptied file applies as a genuinely empty file on target instances, not as a format-encoded literal (e.g. the two characters `""`).

The patch directory path SHALL be defined by a single shared constant; all producers and consumers (patch loading, patch list folder management, draft finalization, recomposition staging) SHALL resolve it through that constant. Installs with a legacy `config/moc/patchs/` directory are migrated per the migration requirement; no fallback read of the legacy location is performed after migration.

Patches SHALL also load from a zip containing the same two flat entries, with the patch name defaulting to the zip basename. All loaders are tolerant: missing or malformed files yield empty entries/metadata rather than errors, and malformed individual entries are skipped.

#### Scenario: Malformed patch entry skipped
- **WHEN** `patch.json` contains an entry with a missing or invalid field
- **THEN** that entry is skipped and the remaining valid entries load

#### Scenario: Single source for the patch directory path
- **WHEN** any component resolves a patch directory
- **THEN** it uses the shared constant, so the location cannot drift between readers and writers

#### Scenario: Patch over an emptied file records the effective type
- **WHEN** a draft staged over a pinned-`json` file whose content is empty is finalized
- **THEN** the patch's `mocmeta.json` records `content=text` for that file, and applying the patch on another instance produces an empty file with no residual diff
