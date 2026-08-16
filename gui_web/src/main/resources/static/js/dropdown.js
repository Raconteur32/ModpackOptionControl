// Unified <ActionDropdown> component (design §4, flow §5). Reused by the file
// tree file row, the main area diff tree rows, and the staging panel rows.
//
// Rendered as plain HTML (renderActionDropdown) + global event delegation
// (initActionDropdowns) so callers don't need to re-bind listeners after every
// re-render.
//
// State/action model (change dropdown-reset-action): the button label shows the
// row's STATE (UNSTAGED renders empty, DEFAULTED/OVERRIDDEN/IGNORED as before),
// while the menu offers ACTIONS derived from that state — including RESET, the
// unified inverse that returns the row to UNSTAGED (unstage or unignore).

import { uiState } from './state.js';
import { requestStage, requestIgnore, requestReset } from './actions.js';

let rerender = () => {};
export function setRerenderCallback(fn) { rerender = fn; }

function dropdownId(filePath, optionPath) { return `${filePath}::${optionPath}`; }

// Actions offered per state: UNSTAGED → stage/ignore; staged → same + RESET;
// IGNORED → RESET only. Exported for unit tests.
export function menuItemsFor(state) {
    if (state === 'IGNORED') return ['RESET'];
    if (state === 'DEFAULTED' || state === 'OVERRIDDEN') return ['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET'];
    return ['DEFAULT', 'OVERRIDE', 'IGNORE'];
}

// Returns the CSS state class + label text for the current state. Exported for tests.
export function displayFor(state) {
    if (state === 'DEFAULTED') return { cls: 'mode-DEFAULT', label: 'DEFAULT' };
    if (state === 'OVERRIDDEN') return { cls: 'mode-OVERRIDE', label: 'OVERRIDE' };
    if (state === 'IGNORED') return { cls: 'mode-IGNORE', label: 'IGNORE \u{1F6AB}' };
    return { cls: '', label: '' };
}

// RESET gets a separator when it follows other actions.
function renderMenu(items, up) {
    return `
        <div class="action-dropdown-menu${up ? ' dropup' : ''}" data-dropdown-menu>
            ${items.map((o, i) => `${o === 'RESET' && i > 0 ? '<div class="menu-sep"></div>' : ''}<div class="opt ${o}" data-select="${o}">${o}</div>`).join('')}
        </div>`;
}

// opts: { filePath, optionPath, state, disabled? }
export function renderActionDropdown(opts) {
    const { filePath, optionPath, state: rowState = 'UNSTAGED', disabled = false } = opts;
    const id = dropdownId(filePath, optionPath);
    const { cls, label } = displayFor(rowState);
    const open = uiState.openDropdown?.id === id;
    const items = menuItemsFor(rowState);
    const menu = open ? renderMenu(items, uiState.openDropdown.up) : '';

    return `
        <div class="action-dropdown" data-dropdown-root data-file="${escapeAttr(filePath)}" data-path="${escapeAttr(optionPath)}" data-menu-size="${items.length}">
            <button class="action-dropdown-btn ${cls}" data-toggle ${disabled ? 'disabled' : ''}>${label}</button>
            ${menu}
        </div>`;
}

function escapeAttr(s) {
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}

// ---------- Menu flip (clipping fix, design D5) ----------
//
// The menu opens downward by default, which lets the enclosing scroll
// container (overflow-y: auto) clip it for rows near the bottom. At toggle
// time we measure the button against its nearest scroll-container ancestor
// and open upward instead when there isn't enough room below (and more room
// above). The direction is stored next to the open id in uiState and consumed
// by the render; rows don't move during the toggle's rerender, so measuring
// the pre-rerender DOM is exact.

const MENU_ROW_H = 30; // approx .opt height (padding + line)
const MENU_PAD = 8;

function nearestScrollBounds(el) {
    let node = el.parentElement;
    while (node) {
        const oy = getComputedStyle(node).overflowY;
        if (oy === 'auto' || oy === 'scroll') return node.getBoundingClientRect();
        node = node.parentElement;
    }
    return { top: 0, bottom: window.innerHeight };
}

function shouldOpenUp(toggle, itemCount) {
    const btn = toggle.getBoundingClientRect();
    const bounds = nearestScrollBounds(toggle);
    const menuH = itemCount * MENU_ROW_H + MENU_PAD;
    const below = bounds.bottom - btn.bottom;
    const above = btn.top - bounds.top;
    return below < menuH && above > below;
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
    const open = uiState.openDropdown?.id === id;

    const menu = open ? renderMenu(options, uiState.openDropdown.up) : '';

    return `
        <div class="action-dropdown" data-bulk-root data-bulk-id="${escapeAttr(id)}" data-menu-size="${options.length}">
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
                uiState.openDropdown = (uiState.openDropdown?.id === id)
                    ? null
                    : { id, up: shouldOpenUp(toggle, Number(bulkRoot.dataset.menuSize) || 3) };
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
            uiState.openDropdown = (uiState.openDropdown?.id === id)
                ? null
                : { id, up: shouldOpenUp(toggle, Number(root.dataset.menuSize) || 3) };
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
            if (choice === 'RESET') requestReset(filePath, optionPath);
            else if (choice === 'IGNORE') requestIgnore(filePath, optionPath);
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
