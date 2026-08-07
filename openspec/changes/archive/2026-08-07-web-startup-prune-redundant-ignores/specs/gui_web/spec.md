# Web GUI Delta

## MODIFIED Requirements

### Requirement: Server startup and configuration

The server SHALL install its platform service, run migrations, run patch-list startup cleanup, prune redundant ignore entries (entries under an ignored directory or covered by a broader permanent/session/value ancestor), apply pending patches, then serve the SPA and API on port `MOC_PORT` (default 7421), opening a browser tab unless `MOC_NO_BROWSER=true` or desktop browsing is unsupported. The game directory resolves from an override variable, the `moc.gameDir` system property, or candidate probing (`.`, `..`, `run`, `../fabric/run`, `../run`); a valid directory contains both `config/` and `mods/`. Failure to resolve SHALL exit with code 1.

At startup the server SHALL regenerate the dev reference tree **only when it is stale**, determined by comparing a stored fingerprint against the current authoring inputs: the ordered patch list, the contents of every patch's `patch.json` and `mocmeta.json`, the configured ignored paths, and the application version. The fingerprint stamp SHALL be written only after a regeneration completes successfully, so an interrupted regeneration forces a full regeneration at the next start.

#### Scenario: Port from environment
- **WHEN** `MOC_PORT=8000` is set
- **THEN** the server listens on port 8000

#### Scenario: Fresh fingerprint skips regeneration
- **WHEN** the server starts and no patch file, the patch list, the ignored paths, or the app version has changed since the last completed regeneration
- **THEN** the dev reference tree is reused as-is and startup does not replay any patches

#### Scenario: Out-of-band patch edit triggers regeneration
- **WHEN** a patch's `patch.json` is edited outside the tool (or the patch list is reordered) while the server is stopped
- **THEN** the next startup detects the fingerprint mismatch and fully regenerates the dev reference tree before serving requests

#### Scenario: Missing or incomplete state regenerates
- **WHEN** the fingerprint stamp is absent (fresh install, prior crash mid-regeneration, or the reference tree was wiped by another tool)
- **THEN** a full regeneration runs and the stamp is written only on completion

#### Scenario: Redundant ignores pruned at startup
- **WHEN** the server starts with ignore entries covered by a broader ignore (e.g. a session ignore on an option whose ancestor is permanently ignored, or any entry under an ignored directory)
- **THEN** the covered entries are removed and the persisted store is updated; entries not covered by any broader ignore are kept
