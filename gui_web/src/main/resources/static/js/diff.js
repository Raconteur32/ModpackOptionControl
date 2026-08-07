// Main area — inline expandable JSON diff tree (flow §8).

import { state, uiState } from './state.js';
import { renderActionDropdown, renderBulkActionDropdown, registerBulkHandler } from './dropdown.js';
import { escapeHtml } from './dialogs.js';
import { isDescendant } from './pathutils.js';
import { requestBulkStage, requestBulkIgnore } from './actions.js';
import { handleSelectClick, selectAll, isSelectAllShortcut } from './selection.js';

let rerenderCallback = () => {};
export function setRerenderCallback(fn) { rerenderCallback = fn; }

const BULK_ID = 'bulk-main';

// Selection scoping index (flow §7/§8 mockup: multi-select limited to the same
// level + same parent node). Rebuilt on every render from the currently
// displayed tree — `scopeOf` maps a row key to its parent's key (or a
// per-file root scope id for depth-0 rows), `siblingsByScope` gives the
// ordered list of row keys sharing that scope (used for shift-range and
// Ctrl/Cmd+A), and `actionOf` gives each row's current dropdown action (used
// to compute the bulk dropdown's common/mixed state).
let selIndex = { scopeOf: new Map(), siblingsByScope: new Map(), actionOf: new Map() };

// True if a node is a leaf ignored entry, or a parent node whose every
// descendant leaf is ignored (flow §9 "Nœud parent dont tous les enfants sont
// ignorés : masqué en Filtered, grisé en Greyed").
function subtreeFullyIgnored(node) {
    if (!node.hasChildren) return node.action === 'IGNORE';
    return node.children.length > 0 && node.children.every(subtreeFullyIgnored);
}

// Nodes hidden in Filtered mode: ignored leaves, and parent nodes whose whole
// subtree is ignored. Everything else (including a parent with a mix of
// ignored/non-ignored children) stays visible.
function hiddenInFiltered(node) {
    return uiState.displayMode === 'FILTERED' && subtreeFullyIgnored(node);
}

function indexChildren(nodes, filePath, scopeId) {
    const visible = nodes.filter(n => !hiddenInFiltered(n));
    const keys = visible.map(n => `${filePath}::${n.path}`);
    selIndex.siblingsByScope.set(scopeId, keys);
    visible.forEach((n, i) => {
        const key = keys[i];
        selIndex.scopeOf.set(key, scopeId);
        selIndex.actionOf.set(key, n.action ?? null);
        if (n.hasChildren) indexChildren(n.children, filePath, key);
    });
}

// Shared with renderPatchEntryRow below: maps a DiffNode/PatchEntry `kind`
// (CHANGED | NEW | DELETED) to the same badge styling used at file level
// (filetree.js's kindPrefix), so option rows carry the same new/changed/
// deleted signal as file rows instead of relying solely on value color.
function kindBadge(kind) {
    if (kind === 'NEW') return { cls: 'new', text: '+ new' };
    if (kind === 'DELETED') return { cls: 'deleted', text: '- deleted' };
    return { cls: 'changed', text: '~ changed' };
}

function fmtValue(v, raw) {
    if (v === null || v === undefined) return '<i>null</i>';
    if (raw) return escapeHtml(JSON.stringify(v));
    if (typeof v === 'string') return escapeHtml(v);
    if (typeof v === 'object') return escapeHtml(JSON.stringify(v));
    return escapeHtml(String(v));
}

// Counts staged descendants (draft entries strictly under `path`, in the same
// file) for the "[n]" partial-stage badge on parent nodes.
function partialStagedCount(filePath, path) {
    return state.draftEntries.filter(e => e.filePath === filePath && isDescendant(e.optionPath, path)).length;
}

function renderNode(node, depth, filePath) {
    if (hiddenInFiltered(node)) return '';

    const key = `${filePath}::${node.path}`;
    // Depth-0 nodes are expanded by default; deeper nodes are collapsed by
    // default. `expandedNodes` stores paths explicitly toggled away from that
    // default (a simple XOR against the default state).
    const defaultExpanded = depth === 0;
    const toggled = uiState.expandedNodes.has(node.path);
    const expanded = node.hasChildren && (toggled ? !defaultExpanded : defaultExpanded);
    const selected = uiState.selectedNodes.has(key);
    const raw = uiState.rawNodes.has(key);
    const partial = node.hasChildren ? partialStagedCount(filePath, node.path) : 0;

    const valueHtml = !node.hasChildren ? renderValue(node, raw) : '';
    const rawToggle = !node.hasChildren && node.kind === 'CHANGED'
        ? `<span class="raw-toggle ${raw ? 'active' : ''}" data-raw-toggle="${escapeHtml(key)}">RAW</span>` : '';

    const dropdown = renderActionDropdown({
        filePath,
        optionPath: node.path,
        action: node.action,
    });

    const kb = kindBadge(node.kind);

    const rowClasses = [
        'row',
        selected ? 'selected' : '',
        subtreeFullyIgnored(node) ? 'ignored-greyed' : '',
    ].join(' ');

    const expandGlyph = node.hasChildren ? (expanded ? '▼' : '▶') : '';

    let html = `
        <div class="diff-node" style="--depth:${depth}">
            <div class="${rowClasses}" data-node-path="${escapeHtml(node.path)}">
                <input type="checkbox" class="row-checkbox" data-select-node="${escapeHtml(key)}" ${selected ? 'checked' : ''}>
                <span class="diff-node-expand ${node.hasChildren ? '' : 'leaf'}" data-toggle-node="${escapeHtml(node.path)}">${expandGlyph}</span>
                <span class="diff-node-label" data-toggle-node="${escapeHtml(node.path)}">${escapeHtml(node.label)}</span>
                <span class="kind-badge kind-badge--${kb.cls}">${kb.text}</span>
                ${partial > 0 ? `<span class="partial-badge">[${partial}]</span>` : ''}
                ${node.unresolved ? `<span class="unresolved-badge">&#9888; unresolved</span>` : ''}
                <span class="diff-node-value">${valueHtml}</span>
                ${rawToggle}
                ${dropdown}
            </div>`;

    if (node.hasChildren && expanded) {
        html += `<div class="diff-node-children">${node.children.map(c => renderNode(c, depth + 1, filePath)).join('')}</div>`;
    }
    html += `</div>`;
    return html;
}

function renderValue(node, raw) {
    if (node.kind === 'NEW') return `<span class="new">${fmtValue(node.newValue, raw)}</span>`;
    if (node.kind === 'DELETED') return `<span class="old">${fmtValue(node.oldValue, raw)}</span>`;
    return `<span class="old">${fmtValue(node.oldValue, raw)}</span><span class="arrow">&rarr;</span><span class="new">${fmtValue(node.newValue, raw)}</span>`;
}

// Bulk dropdown state (design §10 "vide / mode commun / ..."): null if no
// selected row is staged/ignored, the common action if all identical (including
// all-unset), 'MIXED' otherwise.
function bulkModeState(keys) {
    const actions = keys.map(k => selIndex.actionOf.get(k) ?? null);
    const first = actions[0];
    return actions.every(a => a === first) ? first : 'MIXED';
}

function renderMainAreaBulkBar() {
    const keys = [...uiState.selectedNodes];
    if (keys.length < 2) return '';
    const targets = keys.map(k => {
        const filePath = state.currentFile;
        const optionPath = k.slice(filePath.length + 2);
        return { filePath, optionPath };
    });
    registerBulkHandler(BULK_ID, (choice) => {
        if (choice === 'IGNORE') requestBulkIgnore(targets);
        else requestBulkStage(targets, choice);
    });
    const dropdown = renderBulkActionDropdown({
        id: BULK_ID,
        state: bulkModeState(keys),
        options: ['DEFAULT', 'OVERRIDE', 'IGNORE'],
    });
    return `<div class="bulk-action-bar">${keys.length} selected ${dropdown}</div>`;
}

// ---------- Read-only patch view (flow §10 [View]) ----------
//
// A finalized patch has no before/after diff semantics (unlike the working
// draft), so its entries are rendered as a flat read-only list — filePath +
// optionPath + kind badge + the action dropdown in its disabled state (reused
// purely for its "DEFAULT/OVERRIDE/IGNORE" pill styling, not as an interactive
// control) — rather than forced through the diff tree machinery above.

function renderPatchEntryRow(entry) {
    const kp = kindBadge(entry.kind);
    const dropdown = renderActionDropdown({
        filePath: entry.filePath,
        optionPath: entry.optionPath,
        action: entry.mode,
        disabled: true,
    });
    return `
        <div class="row patch-view-row">
            <span class="kind-badge kind-badge--${kp.cls}">${kp.text}</span>
            <span class="entry-file">${escapeHtml(entry.filePath)}</span>
            <span class="entry-path">${escapeHtml(entry.optionPath)}</span>
            ${dropdown}
        </div>`;
}

function renderPatchView() {
    const patch = state.viewedPatch;
    const header = `<div class="file-header">Patch: ${escapeHtml(patch.name)} <span class="readonly-tag">(read-only)</span></div>`;
    if (patch.entries.length === 0) {
        return `${header}<div class="empty-state">This patch has no entries.</div>`;
    }
    return `${header}<div id="diff-tree">${patch.entries.map(renderPatchEntryRow).join('')}</div>`;
}

export function renderMainArea() {
    selIndex = { scopeOf: new Map(), siblingsByScope: new Map(), actionOf: new Map() };

    if (state.viewedPatch) {
        return renderPatchView();
    }

    if (!state.currentFile) {
        return `<div class="empty-state">Select a file from the file tree to browse its diff.</div>`;
    }
    if (state.currentTree.length === 0) {
        return `<div class="file-header">${escapeHtml(state.currentFile)}</div><div class="empty-state">No differences in this file.</div>`;
    }

    indexChildren(state.currentTree, state.currentFile, `ROOT::${state.currentFile}`);

    // Prune selected keys that no longer exist in the current tree (e.g. after
    // an action removed/changed the diff), so the bulk bar/scope logic never
    // sees stale entries.
    for (const key of [...uiState.selectedNodes]) {
        if (!selIndex.scopeOf.has(key)) uiState.selectedNodes.delete(key);
    }

    const body = state.currentTree.map(n => renderNode(n, 0, state.currentFile)).join('');
    return `
        <div class="file-header">${escapeHtml(state.currentFile)}</div>
        ${renderMainAreaBulkBar()}
        <div id="diff-tree">${body}</div>`;
}

// Applies a click on a row's selection checkbox (flow §6), enforcing the main
// area's scope rule (flow §7/§8 mockup: selection limited to the same level +
// same parent node) — switching scope behaves like a fresh plain click.
function selectNode(e, key) {
    const scope = selIndex.scopeOf.get(key);
    const orderedKeys = selIndex.siblingsByScope.get(scope) ?? [key];
    if (uiState.selectedNodes.size > 0) {
        const existingScope = selIndex.scopeOf.get(uiState.selectedNodes.values().next().value);
        if (existingScope !== scope) {
            uiState.selectedNodes.clear();
            uiState.selectionAnchor = null;
        }
    }
    uiState.selectionAnchor = handleSelectClick({
        event: e,
        key,
        orderedKeys,
        selectedSet: uiState.selectedNodes,
        anchor: uiState.selectionAnchor,
    });
}

let initialized = false;
export function initDiffTree() {
    if (initialized) return;
    initialized = true;
    const mainArea = document.getElementById('main-area');

    mainArea.addEventListener('click', (e) => {
        const toggle = e.target.closest('[data-toggle-node]');
        if (toggle) {
            const path = toggle.dataset.toggleNode;
            if (uiState.expandedNodes.has(path)) uiState.expandedNodes.delete(path);
            else uiState.expandedNodes.add(path);
            // Stop propagation before rerendering: the synchronous innerHTML
            // rebuild below detaches e.target from the document, which would
            // otherwise make the document-level "click outside" listener
            // below (checking mainArea.contains(e.target)) misfire.
            e.stopPropagation();
            rerenderCallback();
            return;
        }
        const rawToggle = e.target.closest('[data-raw-toggle]');
        if (rawToggle) {
            const key = rawToggle.dataset.rawToggle;
            if (uiState.rawNodes.has(key)) uiState.rawNodes.delete(key);
            else uiState.rawNodes.add(key);
            e.stopPropagation();
            rerenderCallback();
            return;
        }
        const select = e.target.closest('[data-select-node]');
        if (select) {
            selectNode(e, select.dataset.selectNode);
            e.stopPropagation();
            rerenderCallback();
            return;
        }

        // Click outside a row (but still inside the main area, e.g. empty
        // space or the file header) deselects everything (flow §6), except
        // interacting with the bulk action bar itself or an open dropdown menu.
        if (uiState.selectedNodes.size > 0
            && !e.target.closest('.row')
            && !e.target.closest('.bulk-action-bar')
            && !e.target.closest('[data-dropdown-root]')) {
            uiState.selectedNodes.clear();
            uiState.selectionAnchor = null;
            rerenderCallback();
        }
    });

    // Click outside the main area entirely also deselects (flow §6).
    document.addEventListener('click', (e) => {
        if (uiState.selectedNodes.size > 0 && !mainArea.contains(e.target)) {
            uiState.selectedNodes.clear();
            uiState.selectionAnchor = null;
            rerenderCallback();
        }
    });

    // Tracks which component is "focused" for Ctrl/Cmd+A scoping (flow §6).
    mainArea.addEventListener('mousedown', () => { uiState.focusedComponent = 'main'; });

    document.addEventListener('keydown', (e) => {
        if (uiState.focusedComponent !== 'main' || !isSelectAllShortcut(e)) return;
        if (!state.currentFile) return;
        e.preventDefault();
        // Selects all rows in the scope of the current selection if any,
        // otherwise the top-level rows of the currently displayed file.
        const anchorKey = uiState.selectedNodes.size > 0 ? uiState.selectedNodes.values().next().value : null;
        const scope = anchorKey ? selIndex.scopeOf.get(anchorKey) : `ROOT::${state.currentFile}`;
        const orderedKeys = selIndex.siblingsByScope.get(scope) ?? [];
        selectAll(uiState.selectedNodes, orderedKeys);
        uiState.selectionAnchor = orderedKeys[0] ?? null;
        rerenderCallback();
    });
}
