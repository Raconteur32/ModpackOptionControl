# Fabric Specification

## Purpose

The `fabric` module packages MOC as a Fabric mod and is the runtime that end users experience: on every game launch, before the game or any other mod reads its configuration, it applies the modpack's pending patches to the instance directory. It contains no game-play behavior — it only bridges the `common` engine to the Fabric loader lifecycle.

## Requirements

### Requirement: Pre-launch patch application

The mod SHALL register a preLaunch entrypoint that, before Minecraft's main class runs: installs the Fabric platform service, runs on-disk migrations, then applies all pending patches from the ordered patch list to the game instance directory.

#### Scenario: Pending patches apply on launch
- **WHEN** the game launches with patches in `config/moc/patch-list.json` that are not yet recorded as applied
- **THEN** each pending patch applies in list order and an `Applied patch: <name>` info message is logged per success

#### Scenario: Patch failure is non-fatal to the game
- **WHEN** a patch fails to apply during preLaunch
- **THEN** the failure is logged at error level and the game continues to launch (application of subsequent patches stops per the common module's abort rule, but the game itself is not crashed)

### Requirement: Fabric platform service

The mod SHALL provide the common module's `PlatformService` backed by Fabric Loader: platform name `"Fabric"`, game directory and config directory from `FabricLoader`, and SLF4J logging under the logger name `moc` (critical messages prefixed `[CRITICAL]`).

#### Scenario: Service installed at both entrypoints
- **WHEN** either the preLaunch entrypoint or the main initializer runs
- **THEN** `PlatformService.INSTANCE` is the Fabric implementation

### Requirement: Inert main initializer and mixin

The main mod initializer SHALL have no functional behavior beyond ensuring the platform service is installed and logging a concise initialization message. The mod SHALL NOT register any mixin or mixin configuration: it performs no runtime bytecode modification of the game.

#### Scenario: No game hooks registered
- **WHEN** the mod initializes
- **THEN** no commands, events, screens, mixins, or behavioral game hooks are registered

#### Scenario: No mixin configuration shipped
- **WHEN** the built mod jar is inspected
- **THEN** it contains no mixin configuration file and the mod metadata declares no `mixins` entry

### Requirement: Accurate mod metadata

The mod metadata SHALL describe this mod and only this mod: homepage `https://modrinth.com/mod/moc`, sources and issues pointing at the ModpackOptionControl repository, grammatically correct description, and license `GPL-3.0-only` matching the repository's LICENSE file and README.

#### Scenario: License consistency
- **WHEN** the mod metadata, the root LICENSE file, and the README license section are compared
- **THEN** all three declare GPL-3.0

### Requirement: Self-contained distribution

The built mod jar SHALL embed (jar-in-jar) the `common` module and its third-party parser libraries (JSON5, JsonPath, properties, TOML, charset detection) so the mod has no runtime dependency other than Minecraft, Fabric Loader, Fabric API, and fabric-language-kotlin.

#### Scenario: Mod declares its environment
- **WHEN** the mod metadata is inspected
- **THEN** it declares id `moc`, environment `*` (client and server), and depends on Minecraft, Fabric Loader, Fabric API, fabric-language-kotlin, and Java 25+
