// Patch History (flow §10) — header reflecting the active mode, selectable list
// (checkbox / bullet / name / entry-count badge, sharing selection.js's gesture
// contract with the main area and staging panel), single/bulk action bars, and
// the [View]/[Amend]/[Recompose]/[Delete] actions.
//
// AMEND/RECOMPOSITION-specific header states ("Resume Amend"/"Resume Recomposition",
// flow §10) are phase 6/7 scope — `currentMode()` already resolves them from
// `state.recomp`, so wiring those headers later only means extending the
// `mode === 'AMEND' | 'RECOMPOSITION'` branches below, not restructuring this file.

import { api } from './api.js';
import { state, uiState, currentMode, recompRangeNames } from './state.js';
import { escapeHtml, showDangerConfirmDialog, showDraftConflictDialog } from './dialogs.js';
import { handleSelectClick, selectAll, isSelectAllShortcut } from './selection.js';

let reloadCallback = async () => {};
export function setReloadCallback(fn) { reloadCallback = fn; }
let rerenderCallback = () => {};
export function setRerenderCallback(fn) { rerenderCallback = fn; }
// Fired after a Resume Amend/Resume Recomposition click — lighter than
// reloadCallback since no mode-transition side effect happened server-side,
// just a UI focus reset.
let resumeAmendCallback = async () => {};
export function setResumeAmendCallback(fn) { resumeAmendCallback = fn; }

// Ordered list of patch names as displayed (most recent first) — the scope
// shift-range/Ctrl+A gestures are allowed to span.
function orderedPatchNames() { return [...state.patches].reverse().map(p => p.name); }

// flow §10: Amend is only ever offered on the last (most recent) patch.
function isLastPatch(name) {
    return state.patches.length > 0 && state.patches[state.patches.length - 1].name === name;
}

function entryCountOf(name) {
    return state.patches.find(p => p.name === name)?.entryCount ?? 0;
}

// flow §12c topbar/history label: full source patch names, e.g.
// "002-performance → 003-graphics" (single-patch range shows just that name).
function recompRangeLabel() {
    const r = recompRangeNames();
    if (!r) return '';
    return r.start === r.end ? ` — ${escapeHtml(r.start)}` : ` — ${escapeHtml(r.start)} &rarr; ${escapeHtml(r.end)}`;
}

export function renderHistory() {
    const mode = currentMode();
    const header = mode === 'NEW_PATCH'
        ? `<div class="history-mode-label">&#9679; NEW PATCH<br>${state.draftEntries.length} entries staged</div>`
        : mode === 'AMEND'
            ? `<div class="history-mode-label app-mode-badge app-mode-badge--amend">&#9679; AMEND</div>
               <button class="btn btn-secondary" id="btn-resume-amend">Resume Amend</button>`
            : `<div class="history-mode-label app-mode-badge app-mode-badge--recomp">&#9679; RECOMPOSITION${recompRangeLabel()}</div>
               <button class="btn btn-secondary" id="btn-resume-recomp">Resume Recomposition</button>`;

    // Prune selected names that no longer exist (e.g. after a delete), so the
    // action bar never sees stale entries.
    const validNames = new Set(state.patches.map(p => p.name));
    for (const name of [...uiState.selectedPatches]) {
        if (!validNames.has(name)) uiState.selectedPatches.delete(name);
    }

    const amendedName = mode === 'AMEND' && state.recomp ? state.patches[state.recomp.rangeStart]?.name : null;
    const recompRange = mode === 'RECOMPOSITION' && state.recomp ? [state.recomp.rangeStart, state.recomp.rangeEnd] : null;

    const list = state.patches.length === 0
        ? `<div class="empty-state">No patches yet.</div>`
        : [...state.patches].reverse().map((p, revIdx) => {
            const idx = state.patches.length - 1 - revIdx;
            const selected = uiState.selectedPatches.has(p.name);
            const inProgress = p.name === amendedName;
            const inRange = recompRange && idx >= recompRange[0] && idx <= recompRange[1];
            return `
            <div class="row patch-row ${selected ? 'selected' : ''} ${inRange ? 'in-range' : ''}">
                <input type="checkbox" class="row-checkbox" data-select-patch="${escapeHtml(p.name)}" ${selected ? 'checked' : ''}>
                <span class="bullet">&#9679;</span>
                <span class="row-label">${escapeHtml(p.name)}</span>
                <span class="entry-count-badge">[${p.entryCount}]</span>
                ${inProgress ? `<span class="amend-in-progress-tag">(amend in progress)</span>` : ''}
                ${inRange ? `<span class="amend-in-progress-tag">(in range)</span>` : ''}
            </div>`;
        }).join('');

    return `<div class="history-header">${header}</div>${renderHistoryActionsBar()}${list}`;
}

// Single-selection actions (flow §10) and bulk actions (≥2 selected), rendered
// as a thin bar reusing the same `.bulk-action-bar` chrome as the main area and
// staging bulk bars (design §10) — no bespoke style for this panel.
function renderHistoryActionsBar() {
    const names = [...uiState.selectedPatches];
    if (names.length === 0) return '';

    const mode = currentMode();
    const recomposeDisabledReason = mode !== 'NEW_PATCH'
        ? `Finish or cancel the current ${mode === 'AMEND' ? 'Amend' : 'Recomposition'} session first`
        : '';

    if (names.length === 1) {
        const name = names[0];
        const last = isLastPatch(name);
        const amendDisabledReason = mode !== 'NEW_PATCH'
            ? 'An Amend or Recomposition session is already active'
            : !last
                ? 'Amend is only available on the last patch'
                : '';
        return `<div class="bulk-action-bar">
            <button class="btn btn-secondary" data-history-view="${escapeHtml(name)}">View</button>
            <button class="btn btn-secondary" data-history-amend="${escapeHtml(name)}" ${amendDisabledReason ? 'disabled' : ''}
                title="${escapeHtml(amendDisabledReason)}">Amend</button>
            <button class="btn btn-secondary" data-history-recompose="${escapeHtml(name)}" ${recomposeDisabledReason ? 'disabled' : ''}
                title="${escapeHtml(recomposeDisabledReason)}">Recompose</button>
            <button class="btn btn-danger" data-history-delete="${escapeHtml(name)}">Delete</button>
        </div>`;
    }

    return `<div class="bulk-action-bar">${names.length} selected
        <button class="btn btn-secondary" data-history-bulk-recompose ${recomposeDisabledReason ? 'disabled' : ''}
            title="${escapeHtml(recomposeDisabledReason)}">Recompose</button>
        <button class="btn btn-danger" data-history-bulk-delete>Delete</button>
    </div>`;
}

// [View] (flow §10): loads the finalized patch's entries into the main area,
// read-only — a flat entry list (diff.js#renderPatchView), not the diff tree,
// since a finalized patch has no before/after diff semantics.
async function viewPatch(name) {
    const res = await api.patches.get(name);
    state.viewedPatch = { name: res.name, entries: res.entries ?? [] };
    rerenderCallback();
}

// Amend entry point 2 (flow §12b) — [Amend] on the last patch in the history.
// Only reachable in NEW_PATCH mode (guarded by renderHistoryActionsBar's
// disabled state), matching entry point 1's guard in staging.js.
export async function amendFromHistory(name) {
    if (currentMode() !== 'NEW_PATCH' || !isLastPatch(name)) return;
    const lastIdx = state.patches.length - 1;
    if (state.draftEntries.length === 0) {
        await doStartAmend(lastIdx);
        return;
    }
    showDraftConflictDialog({
        onKeep: () => doStartAmend(lastIdx),
        onOverwrite: async () => {
            const res = await api.draft.finalizeForAmend();
            if (!res.ok) {
                alert(`Could not start amend: ${res.body.error ?? 'unknown error'}`);
                return;
            }
            await doStartAmend(res.body.lastPatchIndex);
        },
    });
}

async function doStartAmend(lastIdx) {
    const res = await api.recomp.start({ startIdx: lastIdx, endIdx: lastIdx, isAmend: true });
    if (!res.ok) {
        alert(`Could not start amend: ${res.body.error ?? 'unknown error'}`);
        return;
    }
    await reloadCallback();
}

async function doResumeAmend() {
    if (currentMode() !== 'AMEND') return;
    await resumeAmendCallback();
}

async function doResumeRecomp() {
    if (currentMode() !== 'RECOMPOSITION') return;
    await resumeAmendCallback();
}

async function doStartRecomp(startIdx, endIdx) {
    const res = await api.recomp.start({ startIdx, endIdx, isAmend: false });
    if (!res.ok) {
        alert(`Could not start recomposition: ${res.body.error ?? 'unknown error'}`);
        return;
    }
    uiState.selectedPatches.clear();
    uiState.patchAnchor = null;
    await reloadCallback();
}

// [Recompose] (flow §12c) — starts a RECOMPOSITION session covering the
// contiguous index range spanning every selected patch (min..max index, so a
// Ctrl+click of non-adjacent patches pulls in everything between them, same
// semantics as the backend's startIdx/endIdx range). Guarded, like Amend, by
// the disabled bulk-action-bar button when a session is already active — the
// alerts here are a defensive fallback against a stale render.
export function recompose(names) {
    const mode = currentMode();
    if (mode === 'AMEND') {
        alert('Finish or cancel the current Amend session before starting a Recomposition.');
        return;
    }
    if (mode === 'RECOMPOSITION') {
        alert('A Recomposition session is already active. Resume or cancel it before starting another.');
        return;
    }
    const indices = names
        .map(n => state.patches.findIndex(p => p.name === n))
        .filter(i => i >= 0);
    if (indices.length === 0) return;
    const startIdx = Math.min(...indices);
    const endIdx = Math.max(...indices);

    if (state.draftEntries.length === 0) {
        doStartRecomp(startIdx, endIdx);
        return;
    }
    // Draft conflict dialog (flow §12c step 3 — same generic dialog as Amend,
    // design §9). "Keep draft" leaves the current DraftPatch untouched (it
    // resurfaces once the recomposition session ends); "Overwrite" discards it
    // since recomposition's own entries come entirely from the source patches,
    // not from the current draft.
    showDraftConflictDialog({
        onKeep: () => doStartRecomp(startIdx, endIdx),
        onOverwrite: async () => {
            await api.draft.clear();
            await doStartRecomp(startIdx, endIdx);
        },
    });
}

// [Delete] — irreversible deletion popup, exact format from flow §10: ⚠ title,
// bullet list of "name (n entries)", "This action cannot be undone.",
// [Delete permanently] (.btn-danger) / [Cancel].
function bulletList(names) {
    return names.map(n => {
        const c = entryCountOf(n);
        return `<div class="delete-item">&middot; ${escapeHtml(n)} (${c} ${c === 1 ? 'entry' : 'entries'})</div>`;
    }).join('');
}

function deletePatches(names) {
    showDangerConfirmDialog({
        title: 'Irreversible deletion',
        bodyHtml: `
            <p>The following patches will be deleted:</p>
            <div class="delete-list">${bulletList(names)}</div>
            <p>This action cannot be undone.</p>`,
        onConfirm: async () => {
            await api.patches.delete(names);
            uiState.selectedPatches.clear();
            if (state.viewedPatch && names.includes(state.viewedPatch.name)) state.viewedPatch = null;
            await reloadCallback();
        },
    });
}

function selectPatchRow(e, name) {
    uiState.patchAnchor = handleSelectClick({
        event: e,
        key: name,
        orderedKeys: orderedPatchNames(),
        selectedSet: uiState.selectedPatches,
        anchor: uiState.patchAnchor,
    });
}

let initialized = false;
export function initHistory() {
    if (initialized) return;
    initialized = true;

    const historyPanel = document.getElementById('patch-history');
    const historyList = document.getElementById('history-list');

    historyList.addEventListener('click', (e) => {
        const select = e.target.closest('[data-select-patch]');
        if (select) {
            selectPatchRow(e, select.dataset.selectPatch);
            // Stop propagation before rerendering, same reason as filetree/diff/staging:
            // the synchronous innerHTML rebuild detaches e.target from the document.
            e.stopPropagation();
            rerenderCallback();
            return;
        }
        const view = e.target.closest('[data-history-view]');
        if (view) { viewPatch(view.dataset.historyView); return; }

        if (e.target.id === 'btn-resume-amend') { doResumeAmend(); return; }
        if (e.target.id === 'btn-resume-recomp') { doResumeRecomp(); return; }

        const amend = e.target.closest('[data-history-amend]');
        if (amend && !amend.disabled) { amendFromHistory(amend.dataset.historyAmend); return; }

        const recomposeBtn = e.target.closest('[data-history-recompose]');
        if (recomposeBtn && !recomposeBtn.disabled) { recompose([recomposeBtn.dataset.historyRecompose]); return; }

        const del = e.target.closest('[data-history-delete]');
        if (del) { deletePatches([del.dataset.historyDelete]); return; }

        const bulkRecompose = e.target.closest('[data-history-bulk-recompose]');
        if (bulkRecompose && !bulkRecompose.disabled) { recompose([...uiState.selectedPatches]); return; }

        const bulkDelete = e.target.closest('[data-history-bulk-delete]');
        if (bulkDelete) { deletePatches([...uiState.selectedPatches]); return; }
    });

    // Click outside a row (but still inside the panel, e.g. the header or empty
    // space) deselects everything, except interacting with the action bar itself.
    historyPanel.addEventListener('click', (e) => {
        if (uiState.selectedPatches.size > 0
            && !e.target.closest('.row')
            && !e.target.closest('.bulk-action-bar')) {
            uiState.selectedPatches.clear();
            uiState.patchAnchor = null;
            rerenderCallback();
        }
    });

    // Click outside the panel entirely also deselects.
    document.addEventListener('click', (e) => {
        if (uiState.selectedPatches.size > 0 && !historyPanel.contains(e.target)) {
            uiState.selectedPatches.clear();
            uiState.patchAnchor = null;
            rerenderCallback();
        }
    });

    // Tracks which component is "focused" for Ctrl/Cmd+A scoping.
    historyPanel.addEventListener('mousedown', () => { uiState.focusedComponent = 'history'; });

    document.addEventListener('keydown', (e) => {
        if (uiState.focusedComponent !== 'history' || !isSelectAllShortcut(e)) return;
        if (state.patches.length === 0) return;
        e.preventDefault();
        const orderedKeys = orderedPatchNames();
        selectAll(uiState.selectedPatches, orderedKeys);
        uiState.patchAnchor = orderedKeys[0] ?? null;
        rerenderCallback();
    });
}
