# Web GUI Delta

## MODIFIED Requirements

### Requirement: Server startup and configuration

The server SHALL install its platform service, run migrations, run patch-list startup cleanup, apply pending patches, then serve the SPA and API on port `MOC_PORT` (default 7421), opening a browser tab unless `MOC_NO_BROWSER=true` or desktop browsing is unsupported. The game directory resolves from an override variable, the `moc.gameDir` system property, or candidate probing (`.`, `..`, `run`, `../fabric/run`, `../run`); a valid directory contains both `config/` and `mods/`. Failure to resolve SHALL exit with code 1.

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

## ADDED Requirements

### Requirement: Embedded authoring engine with shared-state compatibility

The web backend SHALL embed its own implementation of the patch-authoring engine (draft staging, recomposition, dev reference tree) rather than relying on the core runtime module to provide it. Except for conditional reference regeneration, its behavior SHALL remain identical to before the extraction, and it SHALL keep reading and writing the established on-disk authoring state formats and locations so that it remains interchangeable with the desktop GUI on the same instance.

#### Scenario: State interchangeable with the desktop GUI
- **WHEN** a draft or recomposition session is created with the web GUI and the desktop GUI is later opened on the same instance
- **THEN** the desktop GUI restores that draft or session exactly, and vice versa
