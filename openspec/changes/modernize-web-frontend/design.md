# Design: modernize-web-frontend

## Context

See proposal.md → Why. Current front-end: ~2 800 lines of vanilla JS (`gui_web/src/main/resources/static/js/`, innerHTML + global event delegation), ~730 lines of hand-rolled CSS, no build step. Four panels (file tree, diff tree, staging, history) each re-implement selection/focus/rendering; `state.js` keeps four copies of the selection+anchor+expanded triplet; `app.js` wires ~15 `setRerenderCallback`/`setReloadCallback` setters by hand. WebSocket events trigger full re-fetch + full re-render.

## Goals / Non-Goals

**Goals:**
- Standard interaction model (WAI-ARIA tree/listbox) on all four panels, from library components — not hand-written.
- A single shared "data tree/list" abstraction consumed by the four panels; domain logic (staging semantics, ignore states, select-all scoping) stays custom but expressed as controlled props, not DOM code.
- Near-zero CSS authorship: look and density driven by component props and theme config.
- Self-contained jar unchanged from the outside; front-end assets bundled at build time.
- Component/store-level tests in vitest.

**Non-Goals:**
- No REST/WebSocket contract changes; the Ktor server is untouched.
- Desktop `gui` module untouched (deprecated).
- No new user-facing features; this is a rebuild with behavior parity plus the interaction model spec'd in the delta.
- Pixel-perfect reproduction of the current layout — placement may change toward standard patterns (action bar, context menus).

## Decisions

### D1 — Stack: React + TypeScript + Vite + Chakra UI v3

Chakra v3 components are built on Zag.js/Ark state machines; its TreeView is verified (official docs) to provide the full APG keyboard map (arrows, Home/End, typeahead, Shift+Arrow, Ctrl+A), a checked-vs-selected distinction with indeterminate branches (`nodeCheckbox`), disabled nodes, and size/variant/colorPalette props. The family also covers Menu, Context Menu, Dialog (focus trap), Popover, Tooltip — and **Action Bar**, a dedicated bulk-actions-on-selection component matching the target pattern. This gives the interaction model and the visual coherence with almost no CSS.

React over Vue (Naive UI): Chakra's tree interaction is doc-verified (Naive UI's could not be verified during exploration), Action Bar is a built-in, and React's declarative model is closest to the Compose mental model already known from the `gui` module. TypeScript: Chakra/Zag are TS-first; typed machine APIs catch the path/option-key mistakes the vanilla code currently makes at runtime. Vite: default React toolchain, trivial static output for jar embedding.

Alternatives considered: Shoelace (no build, but tree multi-select stays hand-written), Zag headless alone (interaction perfect, but all styling on us — rejected: goal is near-zero CSS), Naive UI (see above), keeping vanilla structured (reproduces the problem).

### D2 — Build pipeline

`gui_web/frontend/` is the Vite project (sources). `vite build` outputs to `gui_web/src/main/resources/static/` (generated, gitignored except `index.html` shell if needed); `shadowJar` embeds the bundles as today — the jar stays autonomous and Ktor serving code is unchanged. Gradle chains the npm build via an Exec task (or documented two-step), and `web-gui-run.sh` invokes it. Dev loop: `vite dev` with a proxy to the Ktor server (HMR), or `vite build --watch` + refresh.

### D3 — Incremental migration with coexistence

One HTML page hosts both worlds during migration: the legacy vanilla app keeps its mount points; each migrated panel is replaced by a React root. Order: **staging panel first (pilot)** — flat list, simplest shape, validates store + api port + Action Bar + menus; then file tree, diff tree (hardest: value columns, RAW toggle, badges, unresolved conflicts, select-all scoping), patch history; finally ignores popovers/dialogs. Legacy `static/js/` files are deleted panel by panel; the CSS files go last.

Coexistence mechanics (validated against the current code):

- **The store is the single source of truth from day one — before any panel migrates.** A legacy adapter step (task 3.2) turns `state.js` into a read-only view over the store (getters); the legacy `onEvent` WS handlers are deleted and the store owns the event→refetch table (ported verbatim from `app.js`). The legacy global `render()` becomes a store subscriber. Legacy panels keep importing `state.js` unchanged. Consequence: no double fetch, no state divergence during the whole migration.
  - *Implementation note (task 3.2)*: writes through the legacy proxies are **silent in-place mutations**, not notifying `setState` calls — legacy code assumes non-notifying writes followed by an explicit `rerender()` (a notifying write fires a synchronous full render between mousedown and click, e.g. on `focusedComponent`, detaching the click target before dispatch). Reads still hit the store live; store actions notify normally.
- **Cross-panel `dropdown.js` dies naturally**: its event delegation matches legacy `data-*` attributes; React rows don't carry them, so the handler simply stops matching panel by panel, and the file is deleted with the last migrated panel.
- **Cross-boundary navigation** (staging → diff scroll/breadcrumb): a store field `focusRequest: { filePath, optionPath, nonce }`. Today `app.js` consumes it (breadcrumb + `scrollIntoView`); when the diff tree migrates, the React tree consumes it. One mechanism, works in both directions during migration.
- The topbar (mode badge, breadcrumb, ignores-area) stays legacy until its dedicated migration step — its containers are isolated, no friction.

### D4 — State layer

A single Zustand store with **two slices**, mirroring the current `state`/`uiState` split:

- `data` slice: recomp, diffFiles, currentFile/currentTree, draftEntries, patches/viewedPatch, ignores/recompIgnores — plus the fetch actions (`reloadDiff()`, `reloadDraft()`…) ported from `app.js`.
- `ui` slice: the four selections + anchors, expansions, popovers, `rawNodes`, `displayMode` — and `breadcrumbPath`, declared properly (it is used in `app.js` today but missing from `state.js`).

Rules:

- **The WS event→refetch table lives in a `sync.ts` module** that binds WS events to store actions. No fetch calls in components, ever. The table is ported verbatim (`diff_changed` → reloadDiff; `ignores_changed` → reloadIgnores + reloadRecompIgnores + reloadDiff; etc.).
- **Selections purge by existence, not visibility**: each reload action intersects the relevant key sets with the fresh data (nodes of `currentTree`, `draftEntries`, patch names, `diffFiles` paths). Three lines per panel, in the same action that sets the data — the invariant stays local. A row hidden by FILTERED mode still exists server-side, so its key survives.
- Domain rules become pure functions over store state + controlled component props: select-all scoping (children of root, never root alone — delta spec), state-aware menu contents (UNSTAGED/DEFAULTED/OVERRIDDEN/IGNORED + RESET), bulk scope, confirmation flows (stage-over-ignore warnings). These functions keep vitest coverage.

### D5 — Shared components and interaction mapping

**Two components + one hook**, not one component with a mode:

- `DataTree` (Chakra TreeView wrapper) for file tree + diff tree; `DataList` for staging + history. Flat lists don't need expansion machinery; a single component would accumulate conditionals.
- A shared hook `useBulkSelection` owns the common logic: checked set handling, Shift ranges, scope rule, select-all override.

Contract (controlled props):

- `checkedIds` / `onCheckedChange` — controlled from the store.
- `getScope(id) => scopeKey` — **injected by the domain**: the diff tree passes "same level, same parent"; staging/history pass a constant (single scope). The rule is not in the component.
- `selectAllScope` — the diff tree passes "root's children"; lists pass "everything".
- `onActivate(item)` — distinct from checking: the file tree hooks "load the diff" here (Zag natively separates `selectedValue` from `checkedValue`, so the contract falls out naturally).
- `renderRow(item, state)` — the tree/list owns interaction, we own cell content.

Interaction mapping (Zag model → MOC model):

- Zag **checked** (checkbox, indeterminate on branches) = MOC bulk-selection checkboxes.
- Zag **selected/focused** = row focus / activation.
- Select-all: Zag's built-in Ctrl+A is overridden via controlled `checkedValue` to apply the scoping rule (children of root when nothing checked; selection scope otherwise).
- Per-row dropdown → Chakra Menu opened from a `[⋯]` affordance visible on hover/focus, plus Context Menu; the state label (DEFAULTED/…) stays as a row badge/tag.
- Diff tree specifics (old→new value column, RAW toggle, ignore/unresolved badges) are custom cell content inside tree rows.

**Dialogs**: a single `ConfirmDialog({ title, body, actions })` wrapper where `actions` is an array of `{ label, colorPalette, onClick }`. The four current helpers become four configurations; the three-button draft-conflict flow (Keep/Overwrite/Cancel) is just a three-item array — Chakra Dialog imposes nothing on button count and its focus trap covers them all. The "⚠ path + reason" effects list stays custom `body` content. Ergonomics rules: `initialFocus` on **Cancel** for destructive dialogs (delete patch, overwrite draft), on Confirm otherwise; Escape closes (= Cancel) everywhere — Chakra default, never disabled.

**Sequential actions** (staging each checked entry, one request per entry in `actions.js`): ported as async store actions (`stageMany(ids)`), best-effort like today — the WS refresh shows the resulting state. Behavior parity first; a partial-failure toast is a stretch item, out of strict scope.

### D6 — Spike gate before commitment

First task is a throwaway-quality spike: scaffold the stack, rebuild the staging panel and a read-only diff tree against a real server, and validate: checkbox tree with custom row content, Action Bar, context menu, keyboard nav, select-all scoping override, bundle size, dev-loop ergonomics. If the spike reveals a blocker (e.g. tree rows can't host the required cell layout), fall back to evaluating Naive UI before proceeding — the task list branches there.

**Gate outcome (task 1.5): GO on Chakra v3.** The spike (`gui_web/frontend/`) validated every risky case against a real server (fake instance, port 7599) and in a real browser (Playwright smoke, 9/9) plus jsdom (5/5): rich tree row content (old→new values, kind badges, per-row Menu inside `TreeView` rows), Action Bar with real bulk RESET, select-all scoping override via controlled `checkedValue`, keyboard nav (arrows/expand/typeahead). Bundle: 177 kB gzip — negligible vs the jar. Not spiked: ContextMenu (same Zag menu machine as Menu — proven; docs-verified).

Spike findings that the implementation must apply:

- **Node checkboxes need the official pattern**: bare `TreeView.NodeCheckbox` renders an empty zero-size span; wrap a Chakra `Checkmark` driven by `useTreeViewNodeContext()` (`checked === true` / `=== 'indeterminate'`).
- **Focus/keyboard target is `data-part=branch-control` for branches** (`role=button`, tabindex), `data-part=item` for leaves. The `data-part=branch` wrapper carries `role=treeitem`/`aria-expanded` but is NOT focusable.
- **Collapsed branches stay in the DOM** under a `hidden` branch-content (removed asynchronously by the collapsible animation) — tests assert visibility, not presence.
- **In jsdom, use `@testing-library/user-event` for ALL Zag interactions** (clicks and keyboard): `fireEvent` does not trigger Zag machine handlers. Plain React handlers (our Ctrl+A capture) work with either.
- **Branch check cascades to descendants** (Zag `toggleBranchChecked`): checking a branch checks its whole subtree. Migration must decide per panel whether to keep cascade (probably yes: "stage this whole section") — semantics note, not a blocker.
- **Select-all override works in capture phase** on a wrapping element (`onKeyDownCapture` + preventDefault/stopPropagation) before Zag's tree handler.

### D7 — Testing strategy

Three levels, no e2e infrastructure:

- **Pure domain functions** — the existing 26 vitest tests are ported to TS nearly as-is (select-all scope, per-state menus, warnings). This layer survives intact.
- **Store actions** — with a mocked `api` module: WS event→refetch table, selection purge, `stageMany`. This is where the new invariants live.
- **Components** — Testing Library, but only on the shared hook/components contract (checking, scope, select-all, Action Bar contents per row state). No exhaustive per-panel render tests — low ROI, high maintenance.

Zag machines run under jsdom (Chakra's own test suite is vitest + jsdom); the spike confirms it with a tree keyboard-navigation test. No Playwright suite: the current exploratory pattern (fake instance + browser driver, see AGENTS.md) remains the final validation; adding e2e infra would be scope creep.

## Risks / Trade-offs

- [Chakra TreeView can't host the diff tree's rich row content (values, RAW toggle, badges, menus) without fighting the component] → D6 spike validates this exact case first, on real data; fallback to Naive UI is decided before any panel is migrated.
- [Coexistence glitches: two selection models / duplicated WS handling while both apps live on one page] → the legacy adapter (D3) makes the store the single source before any panel migrates; `dropdown.js` delegation stops matching on its own; `focusRequest` handles cross-boundary navigation.
- [Dev-loop friction: extra build step before jar] → Gradle chains it; HMR dev server makes most iteration faster than today.
- [Bundle size in the jar] → negligible vs jar size (a few hundred kB min+gzip); verified in the spike.
- [Learning curve / review surface: full front-end rewrite] → panel-by-panel migration keeps diffs reviewable; the pilot panel establishes the patterns the others copy.
- [npm supply chain / lockfile maintenance becomes a project concern] → pinned lockfile, no postinstall scripts policy, dependabot optional later.

## Migration Plan

1. Spike (D6) — decision gate.
2. Pipeline: Vite project, Gradle chaining, `web-gui-run.sh`, AGENTS.md convention update.
3. Store + api/ws port + legacy adapter (`state.js` as store view, WS table into `sync.ts`) + shared `DataTree`/`DataList`/`useBulkSelection`.
4. Panels in order: staging → file tree → diff tree → history → ignores/dialogs.
5. Delete legacy `static/js` + CSS; final exploratory pass (fake instance, all modes: NEW PATCH / AMEND / RECOMPOSITION); update `doc/` front-end docs.

Rollback: until step 5, the legacy app is intact per panel — reverting a panel is a mount-point swap. After step 5, rollback = git revert.
