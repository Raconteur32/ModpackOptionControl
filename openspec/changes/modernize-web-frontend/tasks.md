# Tasks: modernize-web-frontend

## 1. Spike (decision gate — design D6)

- [x] 1.1 Scaffold `gui_web/frontend/` (Vite + React + TS + Chakra UI v3), `vite dev` proxying to a running moc-web server
- [x] 1.2 Spike: staging panel rebuilt against real draft data (list, checkboxes, Action Bar with DEFAULT/OVERRIDE/IGNORE/RESET, per-row menu)
- [x] 1.3 Spike: read-only diff tree with rich row content (old→new values, badges, per-row menu inside tree rows) on a real config file
- [x] 1.4 Spike: validate keyboard nav (arrows/Home/End/typeahead/Shift+arrows), select-all scoping override via controlled checked state, context menu, bundle size, and a Zag tree keyboard test under jsdom (design D7)
- [x] 1.5 Decision gate: GO on Chakra v3, or evaluate Naive UI (Vue) on the same spike cases — record the outcome in design.md before continuing

## 2. Build pipeline (design D2)

- [x] 2.1 Configure `vite build` output into `gui_web/src/main/resources/static/` (generated assets gitignored)
- [x] 2.2 Chain the npm build into the Gradle `:gui_web:shadowJar` flow and update `web-gui-run.sh`
- [x] 2.3 Update AGENTS.md (framework, build/test commands, drop the no-framework convention)
- [x] 2.4 Verify the jar serves the bundled app unchanged from the outside (`MOC_NO_BROWSER=true MOC_PORT=... java -jar ...`)

## 3. State layer + shared components (design D3/D4/D5)

- [x] 3.1 Port `api.js`/`ws.js` to TS modules (fetch wrappers, WS reconnect) — no contract changes
- [x] 3.2 Store (Zustand, `data` + `ui` slices) + `sync.ts` WS table; **legacy adapter**: `state.js` becomes a read-only view over the store, legacy `onEvent` handlers deleted, legacy global `render()` becomes a store subscriber — legacy panels keep working unchanged from here on
- [x] 3.3 Selection purge by existence inside reload actions (tree nodes, draft entries, patch names, file paths); declare `breadcrumbPath` properly in the `ui` slice
- [x] 3.4 `useBulkSelection` hook + `DataTree` (Chakra TreeView wrapper) + `DataList` with controlled props: `checkedIds/onCheckedChange`, `getScope`, `selectAllScope`, `onActivate`, `renderRow`
- [x] 3.5 `ConfirmDialog` wrapper (actions array, `initialFocus` on Cancel for destructive flows) replacing the four legacy dialog helpers' configs; store field `focusRequest` for cross-boundary navigation (legacy `app.js` consumes it for now)
- [x] 3.6 Domain functions with vitest coverage: select-all scope, menu items per row state (UNSTAGED/DEFAULTED/OVERRIDDEN/IGNORED + RESET), stage-over-ignore warning detection; port the 26 existing JS tests to TS

## 4. Panel migration (design D3 — coexistence per mount point)

- [x] 4.1 Staging panel (pilot): `DataList`, per-entry mode switching, provenance, Action Bar bulk actions, remove/RESET, `stageMany` async store action; legacy `staging.js` deleted
- [ ] 4.2 File tree: `DataTree` with directory grouping, `onActivate` = load diff, directory-ignore control, file row states; legacy `filetree.js` deleted
- [ ] 4.3 Diff tree: value columns (leaf-only), RAW toggle, ignore/unresolved/provenance badges, per-row state menu, confirmation flows (stage-over-ignore warnings); React tree takes over `focusRequest`; legacy `diff.js`/`dropdown.js`/`actions.js`/`selection.js` deleted
- [ ] 4.4 Patch history: view (read-only), amend/recompose entry points, delete (single/bulk), unsaved-draft prompt (3-button ConfirmDialog); legacy `history.js` deleted
- [ ] 4.5 Ignores popovers (list, kinds, search, GREYED/FILTERED toggle) and remaining dialogs (finalize, conflict confirmations); legacy `ignores.js`/`dialogs.js` deleted

## 5. Finalization

- [ ] 5.1 Remove remaining legacy `static/js/` and hand-rolled CSS; keep only the bundled app
- [ ] 5.2 Mode badge, breadcrumb, resizable separators re-implemented (or consciously dropped) in the new app
- [ ] 5.3 Exploratory run on a fake instance covering NEW PATCH / AMEND / RECOMPOSITION: stage/ignore/reset per-row and bulk, keyboard-only navigation of a full patch-authoring flow, finalize
- [ ] 5.4 Update `doc/` front-end docs; final `./gradlew :common:test :gui_web:test` + vitest green
