# Proposal: fabric-metadata-housekeeping

## Why

The Fabric module still carries template leftovers and inconsistent metadata: a required-but-empty mixin that must apply for the mod to load, a homepage pointing at a *different mod* (simpleskinswapper), a grammar error in the description, a "Hello Fabric world!" init log, and a license declared three different ways across the repo (LICENSE file says CC0, `fabric.mod.json` says CC0-1.0, README says LGPL-3.0-only) when the intended license is GPL-3.0.

## What Changes

- Delete `ExampleMixin.java`, `moc.mixins.json`, and the `"mixins"` entry in `fabric.mod.json` — the mod performs no bytecode modification and should not carry a required no-op injection.
- `fabric.mod.json` metadata: `homepage` → `https://modrinth.com/mod/moc`; description "An mod…" → "A mod…"; `license` → `"GPL-3.0-only"`.
- Replace the root `LICENSE` file content (CC0 1.0) with the GPL-3.0 text; align the README license section (currently LGPL-3.0-only) to GPL-3.0. The build already bundles `LICENSE` into the jar.
- `ModpackOptionControl.onInitialize`: replace the template greeting with a sensible init log message.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `fabric`: the "inert mixin" requirement is replaced — no mixin is registered at all, and the initializer's logging is no longer template text.

## Impact

- **Code**: `fabric` module only, plus root `LICENSE` and `README.md`. No behavioral change to patch application.
- **Compatibility**: removing the mixin config is safe — the mixin body is empty. Mod metadata consumers (launchers, Modrinth) see corrected license/homepage.
- **Legal note**: relicensing from CC0 to GPL-3.0 is the owner's explicit decision recorded here; all three license declarations become consistent.
