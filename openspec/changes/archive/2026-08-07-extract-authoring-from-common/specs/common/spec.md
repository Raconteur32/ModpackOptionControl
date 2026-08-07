# Common Delta

## REMOVED Requirements

### Requirement: Draft patch staging

**Reason**: Draft staging is authoring-tool machinery, not runtime engine behavior. The core engine (shipped inside the Fabric mod to end users) never stages drafts; only the authoring tools do. The capability relocates to the `gui` and `gui_web` capabilities, which each own an implementation.

**Migration**: Behavior is preserved unchanged in the authoring tools — same persisted draft (`config/moc/dev/patch-draft.json`), same load-time revalidation against the live diff, same finalize and finalize-for-amend semantics. Downstream (the two GUIs) switches from the shared implementation to their own embedded copies; on-disk state is unaffected.

### Requirement: Patch recomposition

**Reason**: Recomposition is authoring-tool machinery, not runtime engine behavior; the Fabric mod's preLaunch path never recomposes patches. The capability relocates to the `gui` and `gui_web` capabilities.

**Migration**: Behavior is preserved unchanged in the authoring tools — same persisted session state (`config/moc/dev/recomposition-draft.json`, `recomp-before`/`recomp-after` trees, `amend/` stash), same conflict detection, auto-staging, provenance, and splice-on-finalize semantics. Downstream switches to per-tool embedded copies; on-disk state is unaffected.
