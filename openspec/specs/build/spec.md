# Build Specification

## Purpose

The build capability covers how the MOC repository is compiled, tested, and packaged: a Gradle multi-module build (Kotlin DSL) producing the Fabric mod jar, the desktop GUI distribution, and the web GUI shadow jar from a shared `common` module.

## Requirements

### Requirement: Multi-module structure

The build SHALL consist of the modules `common`, `fabric`, `gui`, and `gui_web`. All three deliverables (`fabric`, `gui`, `gui_web`) depend on `common`; no deliverable module depends on another deliverable module.

#### Scenario: Module graph
- **WHEN** the Gradle project is configured
- **THEN** `rootProject.name` is `moc` and the included modules are exactly `common`, `fabric`, `gui`, `gui_web`

### Requirement: Centralized version pins

Minecraft, Fabric Loader, Loom, fabric-language-kotlin, Fabric API, and the mod's own version and Maven group SHALL be pinned in `gradle.properties` and consumed via Gradle property providers, so version bumps touch one file.

#### Scenario: Single source of truth
- **WHEN** the Minecraft or loader version changes
- **THEN** only `gradle.properties` is edited

### Requirement: Toolchain

All modules SHALL target Java 25 (release/target/JvmTarget 25) with Kotlin as the primary language.

#### Scenario: Uniform compilation level
- **WHEN** any module is compiled
- **THEN** its bytecode targets JVM 25

### Requirement: Deliverable packaging

- The `fabric` module SHALL build a remapped mod jar via Fabric Loom, embedding `common` and its parser libraries (jar-in-jar), bundling the LICENSE, and expanding `${version}` in `fabric.mod.json` during resource processing.
- The `gui` module SHALL build a Compose for Desktop distribution, including a `packageTarGz` task producing `moc-linux.tar.gz` with a `moc.sh` wrapper, and SHALL honor a `moc.gameDir` property passed through as a JVM system property.
- The `gui_web` module SHALL build a self-contained shadow jar (`moc-web`) with main class `fr.raconteur.moc.web.MainKt`.

#### Scenario: Root build produces the mod
- **WHEN** `gradle build` runs at the root
- **THEN** it depends on and produces the Fabric mod build

#### Scenario: Distribution task
- **WHEN** `gradle dist` runs
- **THEN** both the Fabric mod jar and the desktop GUI tarball are produced

### Requirement: Testability of the core

The `common` module SHALL be unit-testable without Minecraft by installing a test `PlatformService` rooted at a temporary directory. The `gui_web` module SHALL run its tests with `forkEvery = 1` because its state lives in process-global singletons.

#### Scenario: Common tests run headless
- **WHEN** `gradle :common:test` runs
- **THEN** tests exercise diffing, patching, drafts, and migration against temp directories without a game or loader
