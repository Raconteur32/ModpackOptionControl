# GUI Delta

## ADDED Requirements

### Requirement: Embedded authoring engine with shared-state compatibility

The desktop GUI SHALL embed its own implementation of the patch-authoring engine (draft staging, recomposition, dev reference tree) rather than relying on the core runtime module to provide it. Its behavior SHALL remain identical to before the extraction, and it SHALL keep reading and writing the established on-disk authoring state formats and locations so that it remains interchangeable with the web GUI on the same instance.

#### Scenario: State interchangeable with the web GUI
- **WHEN** a draft or recomposition session is created with the desktop GUI and the web GUI is later opened on the same instance
- **THEN** the web GUI restores that draft or session exactly, and vice versa

#### Scenario: Unconditional dev-ref regeneration preserved
- **WHEN** the desktop GUI starts
- **THEN** it regenerates the dev reference tree unconditionally, as before this change
