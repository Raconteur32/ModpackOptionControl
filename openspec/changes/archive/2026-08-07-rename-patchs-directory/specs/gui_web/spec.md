# Web GUI Delta

## MODIFIED Requirements

### Requirement: Session and draft persistence parity

The web backend SHALL operate on the same on-disk state as the desktop GUI (`config/moc/patches/`, `dev/patch-draft.json`, `dev/recomposition-draft.json`, `dev/amend/`, `dev/editor.json`), so either tool sees the other's patches, drafts, and ignores. Drafts and sessions persist across restarts and are revalidated on load.

#### Scenario: Tools share state
- **WHEN** a patch is finalized in the desktop GUI and the web app is opened on the same instance
- **THEN** the web patch history lists the new patch
