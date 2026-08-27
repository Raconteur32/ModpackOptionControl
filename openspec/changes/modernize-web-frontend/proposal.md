# Proposal: modernize-web-frontend

## Why

The `gui_web` SPA was built from scratch in dependency-free vanilla JS (innerHTML + event delegation). Everything works, but the result is impractical to use: no keyboard navigation, four bespoke reimplementations of the same selectable list/tree (file tree, diff tree, staging, history), visually inconsistent panels, and per-row dropdown buttons scattered everywhere instead of standard interaction patterns. Every UI addition currently requires hand-writing interaction logic (focus, selection, menus) case by case. Since the desktop `gui` module is deprecated, `gui_web` is the future of MOC's front-end — it is worth rebuilding on a standard component stack before more features pile onto the current base.

## What Changes

- Rebuild the browser client on **React + TypeScript + Vite**, with **Chakra UI v3** (built on Zag.js/Ark state machines) as the component library: standard TreeView (keyboard navigation per the WAI-ARIA tree view pattern, checkbox multi-select with indeterminate branches), Menu/Context Menu for per-row actions, Dialog/Popover for existing flows, and an Action Bar for bulk actions driven by the checked selection.
- Adopt the standard multi-line list interaction model across all four panels (file tree, diff tree, staging, history): roving focus, arrow/typeahead navigation, Shift/Ctrl range and multi checking, select-all — one shared component instead of four ad-hoc implementations. The existing select-all scoping rule (children of the root, never the root alone) and the state-aware action menu (UNSTAGED/DEFAULTED/OVERRIDDEN/IGNORED + RESET) are preserved.
- **BREAKING (build workflow)**: the front-end gains a build step — Vite compiles `gui_web/frontend/` to bundled assets embedded in the shadow jar; the jar remains fully self-contained and is served exactly as today. The "no build step" convention (AGENTS.md, spec wording) is dropped.
- Migrate panel by panel (staging first, as pilot) with the legacy vanilla app and the new app coexisting during migration; the legacy `static/js/` code is removed once all panels are migrated.
- Front-end tests move from pure-function vitest to store/component-level tests (vitest + Testing Library).
- No REST API or WebSocket contract changes; the desktop `gui` module is untouched.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `gui_web`: the **Browser client** requirement changes — the SPA is now a bundled React/Chakra application (build step), and its interaction model is specified: standard keyboard navigation, checkbox-driven bulk action bar, per-row action menus. Server-side requirements (REST, WebSocket, staging invariants, diff exposure) are unchanged.

## Impact

- **Code**: new `gui_web/frontend/` (Vite project); `gui_web/src/main/resources/static/` replaced by build output; `api.js`/`ws.js` logic ported; `state.js` replaced by a store; legacy `static/js/*.js` and hand-rolled CSS progressively removed.
- **Build**: `gui_web` Gradle build chains the Vite build (or documents the two-step flow); `web-gui-run.sh` updated; npm dependencies with lockfile become part of the repo.
- **Docs**: `AGENTS.md` conventions updated (framework, build, test commands); `doc/` front-end docs updated at migration end.
- **APIs**: none — the server contract is untouched.
- **Users**: same workflows, standard interactions (keyboard navigation, coherent action placement).
