# Fabric Delta

## MODIFIED Requirements

### Requirement: Inert main initializer and mixin

The main mod initializer SHALL have no functional behavior beyond ensuring the platform service is installed and logging a concise initialization message. The mod SHALL NOT register any mixin or mixin configuration: it performs no runtime bytecode modification of the game.

#### Scenario: No game hooks registered
- **WHEN** the mod initializes
- **THEN** no commands, events, screens, mixins, or behavioral game hooks are registered

#### Scenario: No mixin configuration shipped
- **WHEN** the built mod jar is inspected
- **THEN** it contains no mixin configuration file and the mod metadata declares no `mixins` entry

## ADDED Requirements

### Requirement: Accurate mod metadata

The mod metadata SHALL describe this mod and only this mod: homepage `https://modrinth.com/mod/moc`, sources and issues pointing at the ModpackOptionControl repository, grammatically correct description, and license `GPL-3.0-only` matching the repository's LICENSE file and README.

#### Scenario: License consistency
- **WHEN** the mod metadata, the root LICENSE file, and the README license section are compared
- **THEN** all three declare GPL-3.0
