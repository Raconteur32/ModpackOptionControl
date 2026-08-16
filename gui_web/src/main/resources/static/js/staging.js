// Staging panel (flow §11) — list, per-entry dropdown, x remove,
// click-to-navigate, Create New Patch button + dialog.

import { api } from './api.js';
import { state, uiState, currentMode } from './state.js';
import { renderActionDropdown, renderBulkActionDropdown, registerBulkHandler } from './dropdown.js';
import { removeDraftEntryDirect, removeDraftEntriesDirect, setDraftEntriesMode } from './actions.js';
import { escapeHtml, showTextInputDialog, showConfirmDialog } from './dialogs.js';
import { handleSelectClick, selectAll, isSelectAllShortcut } from './selection.js';

let navigateCallback = () => {};
export function setNavigateCallback(fn) { navigateCallback = fn; }
let reloadCallback = async () => {};
export function setReloadCallback(fn) { reloadCallback = fn; }
let rerenderCallback = () => {};
export function setRerenderCallback(fn) { rerenderCallback = fn; }

const BULK_ID = 'bulk-staging';

// Ordered list of "filePath::optionPath" keys currently rendered — the flat
// scope shift-range/Ctrl+A gestures are allowed to span (staging has no
// scoping rule, unlike the main area's same-level/same-parent restriction).
function stagingKey(entry) { return `${entry.filePath}::${entry.optionPath}`; }
function orderedStagingKeys() { return state.draftEntries.map(stagingKey); }

// AMEND provenance is shown as [patch]/[new] (flow §11 "En mode AMEND"), since the
// only two possible sources are the amended patch and the amend dir (literal source
// "amend"). RECOMPOSITION shows the actual source patch name.
function renderSourceTag(entry) {
    if (!entry.source) return '';
    if (currentMode() === 'AMEND') {
        const label = entry.source === 'amend' ? 'new' : 'patch';
        return `<span class="source-tag">[${label}]</span>`;
    }
    return `<span class="source-tag">[source: ${escapeHtml(entry.source)}]</span>`;
}

function renderEntry(entry) {
    const key = stagingKey(entry);
    const selected = uiState.selectedStaging.has(key);
    const dropdown = renderActionDropdown({
        filePath: entry.filePath,
        optionPath: entry.optionPath,
        state: entry.mode === 'DEFAULT' ? 'DEFAULTED' : 'OVERRIDDEN',
    });
    const sourceTag = renderSourceTag(entry);
    return `
        <div class="row ${selected ? 'selected' : ''}" data-staging-row>
            <input type="checkbox" class="row-checkbox" data-select-staging="${escapeHtml(key)}" ${selected ? 'checked' : ''}>
            <span class="row-label" data-navigate="${escapeHtml(entry.filePath)}" data-path="${escapeHtml(entry.optionPath)}">
                ${escapeHtml(entry.filePath)} &nbsp; ${escapeHtml(entry.optionPath)}
            </span>
            ${sourceTag}
            ${dropdown}
            <button class="btn-icon" data-remove="${escapeHtml(entry.filePath)}" data-remove-path="${escapeHtml(entry.optionPath)}" title="Remove">&times;</button>
        </div>`;
}

export function renderStagingTitle() {
    const n = state.draftEntries.length;
    let title = `STAGING — ${n} ${n === 1 ? 'entry' : 'entries'}`;
    const unresolvedCount = state.recomp?.unresolvedConflicts?.length ?? 0;
    if (unresolvedCount > 0) {
        title += ` · ⚠ ${unresolvedCount} unresolved ${unresolvedCount === 1 ? 'conflict' : 'conflicts'}`;
    }
    return title;
}

export function renderStagingList() {
    // Prune selected keys that no longer exist (e.g. after a reload), so the
    // bulk bar never sees stale entries.
    const validKeys = new Set(orderedStagingKeys());
    for (const key of [...uiState.selectedStaging]) {
        if (!validKeys.has(key)) uiState.selectedStaging.delete(key);
    }

    if (state.draftEntries.length === 0) return `<div class="empty-state">No entries staged.</div>`;
    return renderStagingBulkBar() + state.draftEntries.map(renderEntry).join('');
}

// Staging bulk bar (flow §11): mode dropdown (DEFAULT/OVERRIDE, applies to
// all selected) + a separate [Remove] button (no confirmation) — distinct
// from the main area's single unified dropdown.
function renderStagingBulkBar() {
    const keys = [...uiState.selectedStaging];
    if (keys.length < 2) return '';
    const targets = keys.map(k => {
        const idx = k.lastIndexOf('::');
        return { filePath: k.slice(0, idx), optionPath: k.slice(idx + 2) };
    });
    registerBulkHandler(BULK_ID, (choice) => {
        // RESET on staged entries is the same remove as the [Remove] button —
        // batched, no confirmation.
        if (choice === 'RESET') removeDraftEntriesDirect(targets);
        else setDraftEntriesMode(targets, choice);
    });
    const modes = keys.map(k => state.draftEntries.find(e => stagingKey(e) === k)?.mode ?? null);
    const bulkState = modes.every(m => m === modes[0]) ? modes[0] : 'MIXED';
    const dropdown = renderBulkActionDropdown({
        id: BULK_ID,
        state: bulkState,
        options: ['DEFAULT', 'OVERRIDE', 'RESET'],
    });
    return `<div class="bulk-action-bar">${keys.length} selected ${dropdown}
        <button class="btn btn-secondary" id="btn-staging-bulk-remove">Remove</button></div>`;
}

export function renderStagingActions() {
    const mode = currentMode();
    const hasEntries = state.draftEntries.length > 0;

    if (mode === 'AMEND') {
        // flow §11 "Finalize bloqué si conflits non résolus": a single [Finalize]
        // replaces [Create New Patch]/[Amend] while a session is active. [Cancel]
        // is always enabled (even with unresolved conflicts) since it's the only
        // way out of a blocked session — it doesn't route through the Finalize
        // dialog's own Cancel, which is unreachable while Finalize is disabled.
        const unresolvedCount = state.recomp?.unresolvedConflicts?.length ?? 0;
        const blocked = unresolvedCount > 0;
        return `<button class="btn btn-primary" id="btn-finalize-amend" ${blocked ? 'disabled' : ''}
            title="${blocked ? 'Resolve all unresolved conflicts before finalizing' : ''}">Finalize</button>
            <button class="btn btn-secondary" id="btn-cancel-session">Cancel</button>`;
    }
    if (mode === 'RECOMPOSITION') {
        // flow §11 "Finalize bloqué si conflits non résolus": a single [Finalize]
        // replaces [Create New Patch]/[Amend] while a session is active. [Cancel]
        // is always enabled — see AMEND branch above for why.
        const unresolvedCount = state.recomp?.unresolvedConflicts?.length ?? 0;
        const blocked = unresolvedCount > 0;
        return `<button class="btn btn-primary" id="btn-finalize-recomp" ${blocked ? 'disabled' : ''}
            title="${blocked ? 'Resolve all unresolved conflicts before finalizing' : ''}">Finalize</button>
            <button class="btn btn-secondary" id="btn-cancel-session">Cancel</button>`;
    }

    // Amend entry point 1 (flow §12b): implicitly targets the last patch and
    // requires an existing draft to fold in ("avec entries dans DraftPatch"),
    // matching finalize-for-amend's empty_draft guard on the backend.
    const hasLastPatch = state.patches.length > 0;
    const amendDisabledReason = !hasLastPatch
        ? 'Amend requires at least one existing patch'
        : !hasEntries
            ? 'Amend from staging requires at least one staged entry'
            : '';
    return `
        <button class="btn btn-primary" id="btn-create-patch" ${hasEntries ? '' : 'disabled'}>Create New Patch</button>
        <button class="btn btn-secondary" id="btn-amend" ${amendDisabledReason ? 'disabled' : ''}
            title="${escapeHtml(amendDisabledReason)}">Amend</button>`;
}

// Amend entry point 1 (flow §12b) — [Amend] in the staging panel, folds the
// current DraftPatch into the amend dir then starts the amend session on the
// last patch. Guarded by renderStagingActions' disabled state; double-checked
// here in case of a stale render.
async function amendFromStaging() {
    if (state.draftEntries.length === 0 || state.patches.length === 0) return;
    const finalizeRes = await api.draft.finalizeForAmend();
    if (!finalizeRes.ok) {
        alert(`Could not start amend: ${finalizeRes.body.error ?? 'unknown error'}`);
        return;
    }
    const startRes = await api.recomp.start({
        startIdx: finalizeRes.body.lastPatchIndex,
        endIdx: finalizeRes.body.lastPatchIndex,
        isAmend: true,
    });
    if (!startRes.ok) {
        alert(`Could not start amend: ${startRes.body.error ?? 'unknown error'}`);
        return;
    }
    await reloadCallback();
}

// [Finalize] dialog for AMEND (flow §12b) — optional rename pre-filled with the
// current patch name, "N entries (was M)" summary, Confirm Amend / Cancel.
function openFinalizeAmendDialog() {
    const targetName = state.patches[state.recomp?.rangeStart]?.name ?? '';
    const previousCount = state.patches[state.recomp?.rangeStart]?.entryCount ?? 0;
    const n = state.draftEntries.length;
    showTextInputDialog({
        title: `Amend ${targetName}`,
        label: 'Rename (optional):',
        defaultValue: targetName,
        metaHtml: `${n} ${n === 1 ? 'entry' : 'entries'} (was ${previousCount})`,
        confirmLabel: 'Confirm Amend',
        onConfirm: async (name) => {
            const res = await api.recomp.finalize(name);
            if (!res.ok) {
                alert(`Could not finalize amend: ${res.body.error ?? 'unknown error'}`);
                return;
            }
            await reloadCallback();
        },
        // flow §12b: Cancel discards the amend session entirely — clears
        // RecompositionDraft (erasing the amend dir) AND the DraftPatch, returning
        // to a clean NEW_PATCH state rather than resurfacing a stale kept draft.
        onCancel: async () => {
            await api.recomp.cancel();
            await api.draft.clear();
            await reloadCallback();
        },
    });
}

// [Finalize] dialog for RECOMPOSITION (flow §12c) — unlike Amend's optional
// rename, Name is REQUIRED (showTextInputDialog already refuses to submit an
// empty value, and the dialog starts with an empty input rather than a
// pre-filled default), plus "N entries · Replaces: {names}" summary.
function openFinalizeRecompDialog() {
    const start = state.recomp?.rangeStart;
    const end = state.recomp?.rangeEnd;
    const rangeNames = start != null && end != null
        ? state.patches.slice(start, end + 1).map(p => p.name)
        : [];
    const n = state.draftEntries.length;
    showTextInputDialog({
        title: `Recompose ${rangeNames.join(' + ')}`,
        label: 'Name:',
        defaultValue: '',
        metaHtml: `${n} ${n === 1 ? 'entry' : 'entries'} &middot; Replaces: ${escapeHtml(rangeNames.join(', '))}`,
        confirmLabel: 'Confirm',
        onConfirm: async (name) => {
            const res = await api.recomp.finalize(name);
            if (!res.ok) {
                alert(`Could not finalize recomposition: ${res.body.error ?? 'unknown error'}`);
                return;
            }
            await reloadCallback();
        },
        // flow §12c: Cancel discards the recomposition session entirely, returning
        // to a clean NEW_PATCH state rather than resurfacing a stale kept draft.
        onCancel: async () => {
            await api.recomp.cancel();
            await api.draft.clear();
            await reloadCallback();
        },
    });
}

// [Cancel] in the staging actions area (AMEND/RECOMPOSITION) — always available,
// unlike the Finalize dialog's own Cancel which is unreachable while unresolved
// conflicts disable [Finalize]. Same discard semantics as flow §12b/§12c's Cancel:
// clears RecompositionDraft AND the DraftPatch, back to a clean NEW_PATCH state.
function cancelSession() {
    const mode = currentMode();
    showConfirmDialog({
        title: 'Cancel session',
        effects: [{
            title: mode === 'AMEND' ? 'Amend' : 'Recomposition',
            detail: 'The current session and its staged entries will be discarded.',
        }],
        onConfirm: async () => {
            await api.recomp.cancel();
            await api.draft.clear();
            await reloadCallback();
        },
    });
}

function selectStagingRow(e, key) {
    uiState.stagingAnchor = handleSelectClick({
        event: e,
        key,
        orderedKeys: orderedStagingKeys(),
        selectedSet: uiState.selectedStaging,
        anchor: uiState.stagingAnchor,
    });
}

let initialized = false;
export function initStaging() {
    if (initialized) return;
    initialized = true;

    const stagingPanel = document.getElementById('staging-panel');
    const stagingList = document.getElementById('staging-list');

    stagingList.addEventListener('click', (e) => {
        const select = e.target.closest('[data-select-staging]');
        if (select) {
            selectStagingRow(e, select.dataset.selectStaging);
            // Stop propagation before rerendering: the synchronous innerHTML
            // rebuild detaches e.target from the document, which would
            // otherwise make the document-level "click outside" listener
            // (checking stagingPanel.contains(e.target)) misfire.
            e.stopPropagation();
            rerenderCallback();
            return;
        }
        const remove = e.target.closest('[data-remove]');
        if (remove) {
            removeDraftEntryDirect(remove.dataset.remove, remove.dataset.removePath);
            return;
        }
        const nav = e.target.closest('[data-navigate]');
        if (nav) {
            navigateCallback(nav.dataset.navigate, nav.dataset.path);
        }
    });

    document.getElementById('staging-actions').addEventListener('click', (e) => {
        if (e.target.id === 'btn-create-patch') {
            openCreatePatchDialog();
        }
        if (e.target.id === 'btn-amend' && !e.target.disabled) {
            amendFromStaging();
        }
        if (e.target.id === 'btn-finalize-amend' && !e.target.disabled) {
            openFinalizeAmendDialog();
        }
        if (e.target.id === 'btn-finalize-recomp' && !e.target.disabled) {
            openFinalizeRecompDialog();
        }
        if (e.target.id === 'btn-cancel-session') {
            cancelSession();
        }
    });

    stagingPanel.addEventListener('click', (e) => {
        if (e.target.id === 'btn-staging-bulk-remove') {
            const targets = [...uiState.selectedStaging].map(k => {
                const idx = k.lastIndexOf('::');
                return { filePath: k.slice(0, idx), optionPath: k.slice(idx + 2) };
            });
            removeDraftEntriesDirect(targets);
            return;
        }

        // Click outside a row (but still inside the staging panel, e.g. the
        // header or empty space) deselects everything, except interacting
        // with the bulk action bar itself or an open dropdown menu.
        if (uiState.selectedStaging.size > 0
            && !e.target.closest('.row')
            && !e.target.closest('.bulk-action-bar')
            && !e.target.closest('[data-dropdown-root]')) {
            uiState.selectedStaging.clear();
            uiState.stagingAnchor = null;
            rerenderCallback();
        }
    });

    // Click outside the staging panel entirely also deselects.
    document.addEventListener('click', (e) => {
        if (uiState.selectedStaging.size > 0 && !stagingPanel.contains(e.target)) {
            uiState.selectedStaging.clear();
            uiState.stagingAnchor = null;
            rerenderCallback();
        }
    });

    // Tracks which component is "focused" for Ctrl/Cmd+A scoping.
    stagingPanel.addEventListener('mousedown', () => { uiState.focusedComponent = 'staging'; });

    document.addEventListener('keydown', (e) => {
        if (uiState.focusedComponent !== 'staging' || !isSelectAllShortcut(e)) return;
        if (state.draftEntries.length === 0) return;
        e.preventDefault();
        const orderedKeys = orderedStagingKeys();
        selectAll(uiState.selectedStaging, orderedKeys);
        uiState.stagingAnchor = orderedKeys[0] ?? null;
        rerenderCallback();
    });
}


// Create New Patch dialog (flow §12a).
function openCreatePatchDialog() {
    const n = state.draftEntries.length;
    const lastPatch = state.patches.length > 0 ? state.patches[state.patches.length - 1].name : null;
    const metaHtml = `${n} ${n === 1 ? 'entry' : 'entries'}${lastPatch ? ` · Last patch: ${escapeHtml(lastPatch)}` : ''}`;

    showTextInputDialog({
        title: 'Create new patch',
        label: 'Name:',
        metaHtml,
        confirmLabel: 'Create',
        onConfirm: async (name) => {
            const res = await api.draft.finalize(name);
            if (!res.ok) {
                alert(`Could not create patch: ${res.body.error ?? 'unknown error'}`);
                return;
            }
            await reloadCallback();
        },
    });
}
