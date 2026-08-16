// Ignores UI (flow §9, checklist Phase 5): topbar Greyed/Filtered toggle,
// ignore-type popup, [Ignores N▾] popover and the RECOMPOSITION-scoped
// [Recomp ignores ▾] popover.

import { api } from './api.js';
import { state, uiState } from './state.js';
import { escapeHtml } from './dialogs.js';

let reloadCallback = async () => {};
export function setReloadCallback(fn) { reloadCallback = fn; }
let rerender = () => {};
export function setRerenderCallback(fn) { rerender = fn; }

function el(html) {
    const t = document.createElement('template');
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
}

// Best-effort prefill for the Value-kind ignore text input, from the node
// currently displayed in the main area diff tree (if any). Purely a UX
// convenience — the field stays freely editable either way.
function findNodeValue(filePath, optionPath) {
    if (state.currentFile !== filePath) return '';
    function walk(nodes) {
        for (const n of nodes) {
            if (n.path === optionPath) return n.newValue ?? n.oldValue ?? '';
            if (n.hasChildren) {
                const v = walk(n.children);
                if (v !== undefined) return v;
            }
        }
        return undefined;
    }
    return walk(state.currentTree) ?? '';
}

// Ignore-type popup (flow §9 mockup) — SESSION/VALUE/PERMANENT only; the
// DIRECTORY kind moved to the file tree's directory rows (design D4 of
// dropdown-reset-action). Calls onConfirm(kind, targetValue) once confirmed;
// targetValue is only present (and required, non-empty) for kind === 'VALUE'.
export function showIgnoreTypeDialog({ filePath, optionPath, onConfirm }) {
    const root = document.getElementById('dialog-root');
    const defaultValue = escapeHtml(String(findNodeValue(filePath, optionPath)));

    const overlay = el(`
        <div class="dialog-overlay">
            <div class="dialog">
                <h2>Ignore type</h2>
                <div class="ignore-type-options">
                    <label class="ignore-type-opt">
                        <input type="radio" name="ignore-kind" value="SESSION" checked>
                        Session <span class="opt-detail">(until the next finalized patch)</span>
                    </label>
                    <label class="ignore-type-opt">
                        <input type="radio" name="ignore-kind" value="VALUE">
                        Value <span class="opt-detail">(cleared when the value changes)</span>
                    </label>
                    <label class="ignore-type-opt">
                        <input type="radio" name="ignore-kind" value="PERMANENT">
                        Permanent
                    </label>
                </div>
                <input type="text" id="ignore-value-input" placeholder="Target value" value="${defaultValue}" style="display:none">
                <div class="dialog-actions">
                    <button class="btn btn-secondary" data-act="cancel">Cancel</button>
                    <button class="btn btn-primary" data-act="confirm">Confirm</button>
                </div>
            </div>
        </div>
    `);
    root.appendChild(overlay);
    function close() { overlay.remove(); }
    overlay.addEventListener('mousedown', (e) => { if (e.target === overlay) close(); });

    const valueInput = overlay.querySelector('#ignore-value-input');
    overlay.addEventListener('change', (e) => {
        if (e.target.name !== 'ignore-kind') return;
        valueInput.style.display = e.target.value === 'VALUE' ? 'block' : 'none';
    });

    overlay.querySelector('[data-act="cancel"]').addEventListener('click', close);
    overlay.querySelector('[data-act="confirm"]').addEventListener('click', () => {
        const kind = overlay.querySelector('input[name="ignore-kind"]:checked').value;
        if (kind === 'VALUE') {
            const targetValue = valueInput.value.trim();
            if (!targetValue) { valueInput.focus(); return; }
            close();
            onConfirm(kind, targetValue);
            return;
        }
        close();
        onConfirm(kind, undefined);
    });
}

// ---------- Topbar area: Greyed/Filtered toggle + [Ignores N▾] + [Recomp ignores ▾] ----------

function ignoreCount() {
    return state.ignores.entries.length + state.ignores.directories.length;
}

function kindLabel(kind) { return kind.length ? kind[0] + kind.slice(1).toLowerCase() : kind; }

export function renderIgnoresArea() {
    let html = `
        <button class="btn btn-secondary" data-display-mode-toggle title="Toggle ignored entries display">
            ${uiState.displayMode === 'FILTERED' ? 'Filtered' : 'Greyed'} &#8646;
        </button>
        <div class="ignores-menu">
            <button class="btn btn-secondary" data-ignores-toggle>Ignores ${ignoreCount()}&#9662;</button>
            ${uiState.ignoresPopoverOpen ? renderIgnoresPopover() : ''}
        </div>`;
    if (state.recomp) {
        html += `
        <div class="ignores-menu">
            <button class="btn btn-secondary" data-recomp-ignores-toggle>Recomp ignores&#9662;</button>
            ${uiState.recompIgnoresPopoverOpen ? renderRecompIgnoresPopover() : ''}
        </div>`;
    }
    return html;
}

// ---------- [Ignores N▾] popover (flow §9) ----------

function ignoreItems() {
    const entryItems = state.ignores.entries.map(e => ({ filePath: e.filePath, optionPath: e.optionPath, kind: e.kind }));
    const dirItems = state.ignores.directories.map(d => ({ filePath: d, optionPath: '', kind: 'DIRECTORY' }));
    return [...entryItems, ...dirItems];
}

// Rendered separately from the popover chrome so a search keystroke can patch
// just this list's innerHTML (see the `input` listener in initIgnoresUI)
// instead of the whole popover, which would otherwise destroy/recreate the
// search <input> itself on every render() full-innerHTML rebuild and lose
// focus/cursor position.
function renderIgnoresListInner() {
    const search = uiState.ignoresSearch.trim().toLowerCase();
    const items = ignoreItems().filter(it => {
        if (uiState.ignoresFilterKind && it.kind !== uiState.ignoresFilterKind) return false;
        if (search && !it.filePath.toLowerCase().includes(search) && !it.optionPath.toLowerCase().includes(search)) return false;
        return true;
    });
    if (items.length === 0) return `<div class="empty-state">No ignores</div>`;
    return items.map(it => `
        <div class="ignore-entry-row">
            <span class="ignore-entry-kind">[${kindLabel(it.kind)}]</span>
            <span class="ignore-entry-path">${escapeHtml(it.optionPath ? `${it.filePath} › ${it.optionPath}` : it.filePath)}</span>
            <button class="btn btn-icon" data-remove-ignore
                    data-file="${escapeHtml(it.filePath)}" data-path="${escapeHtml(it.optionPath)}" data-kind="${escapeHtml(it.kind)}"
                    title="Remove">&times;</button>
        </div>`).join('');
}

function renderIgnoresPopover() {
    const kinds = ['SESSION', 'VALUE', 'PERMANENT', 'DIRECTORY'];
    return `
        <div class="popover ignores-popover" data-ignores-popover>
            <div class="ignores-popover-controls">
                <input type="text" id="ignores-search-input" placeholder="Search by path" value="${escapeHtml(uiState.ignoresSearch)}">
                <select id="ignores-kind-filter">
                    <option value="" ${!uiState.ignoresFilterKind ? 'selected' : ''}>All types</option>
                    ${kinds.map(k => `<option value="${k}" ${uiState.ignoresFilterKind === k ? 'selected' : ''}>${kindLabel(k)}</option>`).join('')}
                </select>
            </div>
            <label class="ignores-popover-toggle">
                <input type="checkbox" id="ignores-display-toggle" ${uiState.displayMode === 'FILTERED' ? 'checked' : ''}>
                Filtered mode
            </label>
            <div class="ignores-popover-list" data-ignores-list>${renderIgnoresListInner()}</div>
        </div>`;
}

// ---------- [Recomp ignores ▾] popover — RECOMPOSITION/AMEND scope only ----------

function renderRecompIgnoresPopover() {
    const items = state.recompIgnores;
    const list = items.length === 0 ? `<div class="empty-state">No recomposition ignores</div>` : items.map(it => `
        <div class="ignore-entry-row">
            <span class="ignore-entry-path">${escapeHtml(it.optionPath ? `${it.filePath} › ${it.optionPath}` : it.filePath)}</span>
            <button class="btn btn-icon" data-remove-recomp-ignore
                    data-file="${escapeHtml(it.filePath)}" data-path="${escapeHtml(it.optionPath)}"
                    title="Remove">&times;</button>
        </div>`).join('');
    return `<div class="popover ignores-popover" data-recomp-ignores-popover>${list}</div>`;
}

// ---------- Wiring ----------

let initialized = false;
export function initIgnoresUI() {
    if (initialized) return;
    initialized = true;

    document.addEventListener('click', async (e) => {
        const modeToggle = e.target.closest('[data-display-mode-toggle]');
        if (modeToggle) {
            uiState.displayMode = uiState.displayMode === 'FILTERED' ? 'GREYED' : 'FILTERED';
            e.stopPropagation();
            rerender();
            return;
        }

        const toggle = e.target.closest('[data-ignores-toggle]');
        if (toggle) {
            uiState.ignoresPopoverOpen = !uiState.ignoresPopoverOpen;
            uiState.recompIgnoresPopoverOpen = false;
            e.stopPropagation();
            rerender();
            return;
        }

        const recompToggle = e.target.closest('[data-recomp-ignores-toggle]');
        if (recompToggle) {
            uiState.recompIgnoresPopoverOpen = !uiState.recompIgnoresPopoverOpen;
            uiState.ignoresPopoverOpen = false;
            e.stopPropagation();
            rerender();
            return;
        }

        const remove = e.target.closest('[data-remove-ignore]');
        if (remove) {
            e.stopPropagation();
            await api.ignores.remove({ filePath: remove.dataset.file, optionPath: remove.dataset.path, kind: remove.dataset.kind });
            await reloadCallback();
            return;
        }

        const removeRecomp = e.target.closest('[data-remove-recomp-ignore]');
        if (removeRecomp) {
            e.stopPropagation();
            await api.ignores.recomp.remove({ filePath: removeRecomp.dataset.file, optionPath: removeRecomp.dataset.path });
            await reloadCallback();
            return;
        }

        // Click outside either popover (and outside their toggle buttons) closes them.
        if ((uiState.ignoresPopoverOpen || uiState.recompIgnoresPopoverOpen)
            && !e.target.closest('[data-ignores-popover]')
            && !e.target.closest('[data-recomp-ignores-popover]')
            && !e.target.closest('[data-ignores-toggle]')
            && !e.target.closest('[data-recomp-ignores-toggle]')) {
            uiState.ignoresPopoverOpen = false;
            uiState.recompIgnoresPopoverOpen = false;
            rerender();
        }
    });

    document.addEventListener('change', (e) => {
        if (e.target.id === 'ignores-kind-filter') {
            uiState.ignoresFilterKind = e.target.value || null;
            rerender();
            return;
        }
        if (e.target.id === 'ignores-display-toggle') {
            uiState.displayMode = e.target.checked ? 'FILTERED' : 'GREYED';
            rerender();
            return;
        }
    });

    document.addEventListener('input', (e) => {
        if (e.target.id === 'ignores-search-input') {
            uiState.ignoresSearch = e.target.value;
            const list = document.querySelector('[data-ignores-list]');
            if (list) list.innerHTML = renderIgnoresListInner();
        }
    });
}
