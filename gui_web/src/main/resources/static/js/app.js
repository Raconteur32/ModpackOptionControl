// Orchestration and WebSocket event handling (tech §6 app.js).

import { connect, onEvent } from './ws.js';
import { api } from './api.js';
import { state, uiState, currentMode, amendTargetName, recompRangeNames } from './state.js';
import { escapeHtml } from './dialogs.js';

import { renderFileTree, initFileTree, setSelectFileCallback, setRerenderCallback as setFileTreeRerender, setReloadCallback as setFileTreeReload } from './filetree.js';
import { renderMainArea, initDiffTree, setRerenderCallback as setDiffRerender } from './diff.js';
import { renderStagingTitle, renderStagingList, renderStagingActions, initStaging, setNavigateCallback, setReloadCallback as setStagingReload, setRerenderCallback as setStagingRerender } from './staging.js';
import { renderHistory, initHistory, setReloadCallback as setHistoryReload, setRerenderCallback as setHistoryRerender } from './history.js';
import { initActionDropdowns, setRerenderCallback as setDropdownRerender } from './dropdown.js';
import { setReloadCallback as setActionsReload } from './actions.js';
import { renderIgnoresArea, initIgnoresUI, setReloadCallback as setIgnoresReload, setRerenderCallback as setIgnoresRerender } from './ignores.js';
import { setResumeAmendCallback } from './history.js';
import { makeReloadDiffFiles, makeReloadDraft } from './reload.js';

// ---------- Data loading (tech §6) ----------

async function loadDiffFile(path) {
    if (path === null) { state.currentFile = null; state.currentTree = []; return; }
    const mode = currentMode();
    const res = mode === 'NEW_PATCH' ? await api.diff.file(path) : await api.recomp.diff.file(path);
    state.currentFile = path;
    state.currentTree = res.tree ?? [];
}

const reloadDiffFiles = makeReloadDiffFiles({ api, state, currentMode });
const reloadDraft = makeReloadDraft({ api, state, currentMode });

async function reloadPatches() {
    state.patches = (await api.patches.list()).patches;
    // The patch currently open in the read-only [View] may have been deleted
    // (e.g. by another client) — drop it rather than show stale content.
    if (state.viewedPatch && !state.patches.some(p => p.name === state.viewedPatch.name)) {
        state.viewedPatch = null;
    }
}

async function reloadIgnores() {
    state.ignores = await api.ignores.get();
}

// Recomposition ignores are scoped to an active RECOMPOSITION/AMEND session
// (flow §9 "Recomp ignores"); outside of one there's nothing to fetch.
async function reloadRecompIgnores() {
    if (!state.recomp) { state.recompIgnores = []; return; }
    state.recompIgnores = (await api.ignores.recomp.get()).entries ?? [];
}

async function reloadRecomp() {
    state.recomp = await api.recomp.get();
}

async function reloadDiff() {
    await reloadDiffFiles();
    if (state.currentFile) await loadDiffFile(state.currentFile);
}

async function loadAll() {
    state.recomp = await api.recomp.get();
    await reloadDiffFiles(false);
    await reloadDraft(false);
    state.patches = (await api.patches.list()).patches;
    state.ignores = await api.ignores.get();
    await reloadRecompIgnores();
    render();
}

// Drops any file/patch focus that may no longer be valid after a mode
// transition (e.g. AMEND session start/finalize/cancel), then re-selects the
// currently open file (if still present) against the new mode's diff source.
async function resetMainAreaFocus() {
    state.viewedPatch = null;
    const path = state.currentFile;
    state.currentFile = null;
    state.currentTree = [];
    uiState.breadcrumbPath = null;
    if (path && state.diffFiles.some(f => f.path === path)) {
        await loadDiffFile(path);
    }
}

// ---------- WebSocket events (tech §6 table "Ressources re-fetchées") ----------

onEvent('diff_changed', async () => { await reloadDiff(); render(); });
onEvent('draft_changed', async () => { await reloadDraft(); render(); });
onEvent('patches_changed', async () => { await reloadPatches(); render(); });
onEvent('recomp_changed', async () => { await loadAll(); });
onEvent('ignores_changed', async () => { await reloadIgnores(); await reloadRecompIgnores(); await reloadDiff(); render(); });
onEvent('conflicts_changed', async () => { await reloadRecomp(); render(); });

// ---------- Rendering ----------

function renderTopbar() {
    const mode = currentMode();
    const badge = document.getElementById('app-mode-badge');
    if (mode === 'AMEND') {
        const name = amendTargetName();
        badge.innerHTML = `<span class="app-mode-badge app-mode-badge--amend">&#9679; AMEND${name ? ` — ${escapeHtml(name)}` : ''}</span>`;
    } else if (mode === 'RECOMPOSITION') {
        const r = recompRangeNames();
        const range = r ? ` — ${escapeHtml(r.start)}${r.start !== r.end ? ` &rarr; ${escapeHtml(r.end)}` : ''}` : '';
        badge.innerHTML = `<span class="app-mode-badge app-mode-badge--recomp">&#9679; RECOMPOSITION${range}</span>`;
    } else {
        badge.innerHTML = '';
    }
    document.getElementById('breadcrumb').innerHTML = renderBreadcrumb();
    document.getElementById('ignores-area').innerHTML = renderIgnoresArea();
}

// Ancestor JSON-path segments of `path`, e.g. "$.client.maxFps" ->
// ["$", "$.client", "$.client.maxFps"]. Handles bracket segments too.
function ancestorPaths(fullPath) {
    const result = ['$'];
    let i = 1;
    while (i < fullPath.length) {
        if (fullPath[i] === '.') {
            let j = i + 1;
            while (j < fullPath.length && fullPath[j] !== '.' && fullPath[j] !== '[') j++;
            result.push(fullPath.slice(0, j));
            i = j;
        } else if (fullPath[i] === '[') {
            let j = fullPath.indexOf(']', i);
            j = (j === -1) ? fullPath.length : j + 1;
            result.push(fullPath.slice(0, j));
            i = j;
        } else {
            i++;
        }
    }
    return result;
}

function renderBreadcrumb() {
    if (!state.currentFile) return '';
    let html = `<span>${escapeHtml(state.currentFile)}</span>`;
    const focusPath = uiState.breadcrumbPath;
    if (focusPath && focusPath !== '$') {
        const segs = ancestorPaths(focusPath);
        html += ` <span class="crumb-sep">&rsaquo;</span> ` +
            segs.map(p => `<span class="crumb" data-crumb="${escapeHtml(p)}">${escapeHtml(p)}</span>`).join(`<span class="crumb-sep">.</span>`);
    }
    return html;
}

function render() {
    renderTopbar();
    document.getElementById('file-tree-list').innerHTML = renderFileTree();
    document.getElementById('main-area-content').innerHTML = renderMainArea();
    document.getElementById('history-list').innerHTML = renderHistory();
    document.getElementById('staging-title').textContent = renderStagingTitle();
    document.getElementById('staging-actions').innerHTML = renderStagingActions();
    document.getElementById('staging-list').innerHTML = renderStagingList();
}

// ---------- Wiring ----------

setSelectFileCallback(async (path) => {
    state.viewedPatch = null; // leave the read-only patch view (if any) to browse the diff tree
    await loadDiffFile(path);
    uiState.breadcrumbPath = null;
    render();
});
setFileTreeRerender(render);
// Directory-ignore button in the file tree: DIRECTORY ignores reload both
// filesystems server-side, so refresh diff + ignores (the matching WS events
// would arrive too — this just makes the refresh deterministic).
setFileTreeReload(async () => { await reloadIgnores(); await reloadDiff(); render(); });
setDiffRerender(render);
setStagingRerender(render);
setDropdownRerender(render);
setHistoryRerender(render);
// History/staging reloads now cover mode-transition actions (start/resume/cancel/finalize
// amend), so they refresh the full state (incl. state.recomp) and drop stale main-area focus.
setHistoryReload(async () => { await loadAll(); await resetMainAreaFocus(); render(); });
setActionsReload(async () => {
    await reloadDiff();
    await reloadDraft();
    await reloadIgnores();
    if (state.recomp) {
        await reloadRecomp();
        await reloadRecompIgnores();
    }
    render();
});
setStagingReload(async () => { await loadAll(); await resetMainAreaFocus(); render(); });
setResumeAmendCallback(async () => { await resetMainAreaFocus(); render(); });
setIgnoresRerender(render);
setIgnoresReload(async () => { await reloadIgnores(); await reloadRecompIgnores(); await reloadDiff(); render(); });
setNavigateCallback(async (filePath, optionPath) => {
    state.viewedPatch = null;
    if (state.currentFile !== filePath) await loadDiffFile(filePath);
    uiState.breadcrumbPath = optionPath;
    render();
    const target = document.querySelector(`[data-node-path="${CSS.escape(optionPath)}"]`);
    target?.scrollIntoView({ block: 'center' });
});

document.getElementById('breadcrumb').addEventListener('click', (e) => {
    const crumb = e.target.closest('[data-crumb]');
    if (!crumb) return;
    uiState.breadcrumbPath = crumb.dataset.crumb;
    render();
    const target = document.querySelector(`[data-node-path="${CSS.escape(crumb.dataset.crumb)}"]`);
    target?.scrollIntoView({ block: 'center' });
});

initFileTree();
initDiffTree();
initStaging();
initHistory();
initActionDropdowns();
initIgnoresUI();
initResizableSeparators();

connect();
loadAll();

// ---------- Draggable separators (design §2) ----------

function initResizableSeparators() {
    dragSeparator(document.getElementById('sep-filetree'), 'v', (dx) => {
        const el = document.getElementById('file-tree');
        setWidth(el, dx, 120, 400);
    });
    dragSeparator(document.getElementById('sep-history'), 'v', (dx) => {
        const el = document.getElementById('patch-history');
        setWidth(el, -dx, 150, 400);
    });
    dragSeparator(document.getElementById('sep-staging'), 'h', (dy, startHeightPx, containerHeight) => {
        const el = document.getElementById('staging-panel');
        const newHeightPx = startHeightPx - dy;
        const min = 120;
        const max = containerHeight * 0.6;
        el.style.height = `${Math.max(min, Math.min(max, newHeightPx))}px`;
    });
}

function setWidth(el, dx, min, max) {
    const current = el.getBoundingClientRect().width;
    const next = Math.max(min, Math.min(max, current + dx));
    el.style.width = `${next}px`;
}

function dragSeparator(handle, axis, onMove) {
    let startX = 0, startY = 0, startHeightPx = 0;
    let lastX = 0, lastY = 0;
    const body = document.getElementById('body');

    handle.addEventListener('mousedown', (e) => {
        e.preventDefault();
        handle.classList.add('dragging');
        startX = lastX = e.clientX;
        startY = lastY = e.clientY;
        startHeightPx = document.getElementById('staging-panel').getBoundingClientRect().height;
        document.body.style.userSelect = 'none';

        function onMouseMove(ev) {
            if (axis === 'v') {
                const dx = ev.clientX - lastX;
                lastX = ev.clientX;
                onMove(dx);
            } else {
                const dy = ev.clientY - lastY;
                lastY = ev.clientY;
                onMove(dy, startHeightPx, body.getBoundingClientRect().height);
                startHeightPx = document.getElementById('staging-panel').getBoundingClientRect().height;
            }
        }
        function onMouseUp() {
            handle.classList.remove('dragging');
            document.body.style.userSelect = '';
            window.removeEventListener('mousemove', onMouseMove);
            window.removeEventListener('mouseup', onMouseUp);
        }
        window.addEventListener('mousemove', onMouseMove);
        window.addEventListener('mouseup', onMouseUp);
    });
}
