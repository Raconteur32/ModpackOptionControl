// Orchestration legacy — allégée (change modernize-web-frontend, design D3).
// Le chargement des données, la table WS et la purge des sélections vivent
// désormais dans le store Zustand du bundle React (gui_web/frontend/src/
// store.ts + sync.ts) ; ce module ne garde que le rendu des panneaux legacy
// et le wiring de leurs callbacks vers les actions du store.

import { state, uiState, currentMode, amendTargetName, recompRangeNames } from './state.js';
import { escapeHtml } from './dialogs.js';

import { renderFileTree, initFileTree, setSelectFileCallback, setRerenderCallback as setFileTreeRerender, setReloadCallback as setFileTreeReload } from './filetree.js';
import { renderMainArea, initDiffTree, setRerenderCallback as setDiffRerender } from './diff.js';
import { renderHistory, initHistory, setReloadCallback as setHistoryReload, setRerenderCallback as setHistoryRerender } from './history.js';
import { initActionDropdowns, setRerenderCallback as setDropdownRerender } from './dropdown.js';
import { setReloadCallback as setActionsReload } from './actions.js';
import { renderIgnoresArea, initIgnoresUI, setReloadCallback as setIgnoresReload, setRerenderCallback as setIgnoresRerender } from './ignores.js';
import { setResumeAmendCallback } from './history.js';

const store = () => window.__moc.getState();

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
    // Le panneau staging est React (task 4.1) — plus de rendu legacy ici.
}

// Le render global legacy est un subscriber du store (design D3) : toute
// mutation d'état — WS, action store, écriture via l'adaptateur state.js —
// déclenche un re-rendu des panneaux legacy.
window.__moc.subscribe(() => render());

// ---------- focusRequest (navigation cross-frontière, design D3/D5) ----------
// Un panneau (staging legacy aujourd'hui, React demain) demande le focus d'une
// option via store.requestFocus(filePath, optionPath) ; ici on consomme :
// charger le fichier si besoin, breadcrumb, scroll vers la ligne.

let lastFocusNonce = 0;
window.__moc.subscribe((s) => {
    const fr = s.ui.focusRequest;
    if (!fr || fr.nonce === lastFocusNonce) return;
    lastFocusNonce = fr.nonce;
    void consumeFocusRequest(fr);
});

async function consumeFocusRequest({ filePath, optionPath }) {
    state.viewedPatch = null; // quitte la vue [View] read-only (le cas échéant)
    if (state.currentFile !== filePath) await store().loadDiffFile(filePath);
    uiState.breadcrumbPath = optionPath; // mutation silencieuse (adaptateur)
    render(); // explicite : quand le fichier était déjà chargé, rien ne notifie
    requestAnimationFrame(() => {
        document.querySelector(`[data-node-path="${CSS.escape(optionPath)}"]`)
            ?.scrollIntoView({ block: 'center' });
    });
}

// ---------- Wiring ----------

setSelectFileCallback(async (path) => {
    await store().selectFile(path); // viewedPatch=null + breadcrumb reset + load
});
setFileTreeRerender(render);
// Directory-ignore button in the file tree: DIRECTORY ignores reload both
// filesystems server-side, so refresh diff + ignores (the matching WS events
// would arrive too — this just makes the refresh deterministic).
setFileTreeReload(async () => { await store().reloadIgnores(); await store().reloadDiff(); });
setDiffRerender(render);
setDropdownRerender(render);
setHistoryRerender(render);
// History/staging reloads cover mode-transition actions (start/resume/cancel/
// finalize amend): full state refresh (incl. recomp) + drop stale main-area focus.
setHistoryReload(async () => { await store().loadAll(); await store().resetMainAreaFocus(); });
setActionsReload(async () => { await store().reloadAfterAction(); });
setResumeAmendCallback(async () => { await store().resetMainAreaFocus(); });
setIgnoresRerender(render);
setIgnoresReload(async () => { await store().reloadIgnores(); await store().reloadRecompIgnores(); await store().reloadDiff(); });

document.getElementById('breadcrumb').addEventListener('click', (e) => {
    const crumb = e.target.closest('[data-crumb]');
    if (!crumb) return;
    uiState.breadcrumbPath = crumb.dataset.crumb; // silencieux → render explicite
    render();
    requestAnimationFrame(() => {
        document.querySelector(`[data-node-path="${CSS.escape(crumb.dataset.crumb)}"]`)
            ?.scrollIntoView({ block: 'center' });
    });
});

initFileTree();
initDiffTree();
initHistory();
initActionDropdowns();
initIgnoresUI();
initResizableSeparators();

// Premier rendu (les données arrivent via sync.ts -> store -> subscription).
render();

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
