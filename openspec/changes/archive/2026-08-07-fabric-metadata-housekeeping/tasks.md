# Tasks: fabric-metadata-housekeeping

## 1. Remove the inert mixin

- [x] 1.1 Delete `fabric/src/main/java/fr/raconteur/moc/mixin/ExampleMixin.java`
- [x] 1.2 Delete `fabric/src/main/resources/moc.mixins.json` and remove the `"mixins"` entry from `fabric.mod.json`

## 2. Metadata and license alignment

- [x] 2.1 `fabric.mod.json`: homepage → `https://modrinth.com/mod/moc`, description "An mod…" → "A mod…", license → `GPL-3.0-only`
- [x] 2.2 Replace root `LICENSE` content with the GPL-3.0 license text
- [x] 2.3 Update the README license section (currently LGPL-3.0-only) to GPL-3.0

## 3. Initializer log

- [x] 3.1 Replace the "Hello Fabric world!" greeting in `ModpackOptionControl.onInitialize` with a concise init message

## 4. Verification

- [x] 4.1 `gradle :fabric:build` green; built jar contains no `moc.mixins.json` and bundles the GPL-3.0 LICENSE
- [x] 4.2 Smoke-launch a dev instance: mod loads, pending patches still apply, no mixin errors in the log
- [x] 4.3 `openspec validate fabric-metadata-housekeeping` passes
