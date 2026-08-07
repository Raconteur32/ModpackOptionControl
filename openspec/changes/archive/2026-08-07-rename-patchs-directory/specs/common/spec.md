# Common Delta

## MODIFIED Requirements

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
