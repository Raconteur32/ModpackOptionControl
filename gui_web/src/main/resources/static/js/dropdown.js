// Unified <ActionDropdown> component (design §4, flow §5). Reused by the file
// tree file row, the main area diff tree rows, and the staging panel rows.
//
// Rendered as plain HTML (renderActionDropdown) + global event delegation
// (initActionDropdowns) so callers don't need to re-bind listeners after every
// re-render.

import { uiState } from './state.js';
import { requestStage, requestIgnore } from './actions.js';

let rerender = () => {};
export function setRerenderCallback(fn) { rerender = fn; }

function dropdownId(filePath, optionPath) { return `${filePath}::${optionPath}`; }

// Returns the CSS state class + label text for the current action.
function displayFor(action) {
    if (action === 'DEFAULT') return { cls: 'mode-DEFAULT', label: 'DEFAULT' };
    if (action === 'OVERRIDE') return { cls: 'mode-OVERRIDE', label: 'OVERRIDE' };
    if (action === 'IGNORE') return { cls: 'mode-IGNORE', label: 'IGNORE \u{1F6AB}' };
    return { cls: '', label: '' };
}

// opts: { filePath, optionPath, action, disabled? }
export function renderActionDropdown(opts) {
    const { filePath, optionPath, action, disabled = false } = opts;
    const id = dropdownId(filePath, optionPath);
    const { cls, label } = displayFor(action ?? null);
    const open = uiState.openDropdown === id;

    const menu = open ? `
        <div class="action-dropdown-menu" data-dropdown-menu>
            <div class="opt DEFAULT" data-select="DEFAULT">DEFAULT</div>
            <div class="opt OVERRIDE" data-select="OVERRIDE">OVERRIDE</div>
            <div class="opt IGNORE" data-select="IGNORE">IGNORE</div>
        </div>` : '';

    return `
        <div class="action-dropdown" data-dropdown-root data-file="${escapeAttr(filePath)}" data-path="${escapeAttr(optionPath)}">
            <button class="action-dropdown-btn ${cls}" data-toggle ${disabled ? 'disabled' : ''}>${label}</button>
            ${menu}
        </div>`;
}

function escapeAttr(s) {
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}

// ---------- Bulk mass-action dropdown (flow §8 main area / §11 staging) ----------
//
// Same visual component as the unified dropdown (design §4 "Mass action, mixed
// states" row: empty / common mode / "..." for mixed), but driven by a caller-
// supplied handler instead of the single-entry requestStage/requestIgnore, since
// a bulk choice applies to a whole selection rather than one filePath/optionPath.

const bulkHandlers = new Map(); // id -> (choice: string) => void

export function registerBulkHandler(id, handler) { bulkHandlers.set(id, handler); }

// state: null (nothing staged/ignored in the selection) | 'MIXED' | 'DEFAULT' | 'OVERRIDE' | 'IGNORE'
export function renderBulkActionDropdown({ id, state, options }) {
    const cls = state === 'MIXED' ? 'mode-MIXED' : (state ? `mode-${state}` : '');
    const label = state === 'MIXED' ? '...' : (state ?? '');
    const open = uiState.openDropdown === id;

    const menu = open ? `
        <div class="action-dropdown-menu" data-dropdown-menu>
            ${options.map(o => `<div class="opt ${o}" data-select="${o}">${o}</div>`).join('')}
        </div>` : '';

    return `
        <div class="action-dropdown" data-bulk-root data-bulk-id="${escapeAttr(id)}">
            <button class="action-dropdown-btn ${cls}" data-toggle>${label}</button>
            ${menu}
        </div>`;
}

let initialized = false;
export function initActionDropdowns() {
    if (initialized) return;
    initialized = true;

    document.addEventListener('click', (e) => {
        const bulkRoot = e.target.closest('[data-bulk-root]');
        if (bulkRoot) {
            const id = bulkRoot.dataset.bulkId;
            const toggle = e.target.closest('[data-toggle]');
            if (toggle) {
                uiState.openDropdown = (uiState.openDropdown === id) ? null : id;
                e.stopPropagation();
                rerender();
                return;
            }
            const opt = e.target.closest('[data-select]');
            if (opt) {
                uiState.openDropdown = null;
                e.stopPropagation();
                bulkHandlers.get(id)?.(opt.dataset.select);
                rerender();
                return;
            }
        }

        const toggle = e.target.closest('[data-toggle]');
        if (toggle) {
            if (toggle.disabled) return;
            const root = toggle.closest('[data-dropdown-root]');
            const id = dropdownId(root.dataset.file, root.dataset.path);
            uiState.openDropdown = (uiState.openDropdown === id) ? null : id;
            e.stopPropagation();
            rerender();
            return;
        }

        const opt = e.target.closest('[data-select]');
        if (opt) {
            const root = opt.closest('[data-dropdown-root]');
            const filePath = root.dataset.file;
            const optionPath = root.dataset.path;
            const choice = opt.dataset.select;
            uiState.openDropdown = null;
            e.stopPropagation();
            if (choice === 'IGNORE') requestIgnore(filePath, optionPath);
            else requestStage(filePath, optionPath, choice);
            rerender();
            return;
        }

        // Click outside any dropdown closes the open one.
        if (uiState.openDropdown !== null && !e.target.closest('[data-dropdown-root]') && !e.target.closest('[data-bulk-root]')) {
            uiState.openDropdown = null;
            rerender();
        }
    });
}
