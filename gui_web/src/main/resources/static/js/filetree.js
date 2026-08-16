// File tree (flow §7): directory grouping/expand-collapse, kind badges,
// [n staged] badge, per-file unified dropdown, click-to-load diff.

import { state, uiState } from './state.js';
import { renderActionDropdown, renderBulkActionDropdown, registerBulkHandler } from './dropdown.js';
import { rootPathForFile, requestBulkStage, requestBulkIgnore, requestBulkReset, resolveRowState } from './actions.js';
import { escapeHtml } from './dialogs.js';
import { handleSelectClick, selectAll, isSelectAllShortcut } from './selection.js';
import { api } from './api.js';

let selectFileCallback = () => {};
export function setSelectFileCallback(fn) { selectFileCallback = fn; }
let rerenderCallback = () => {};
export function setRerenderCallback(fn) { rerenderCallback = fn; }
let reloadCallback = async () => {};
export function setReloadCallback(fn) { reloadCallback = fn; }

const BULK_ID = 'bulk-filetree';

function kindPrefix(kind) {
    if (kind === 'NEW') return { sym: '+', cls: 'new', text: '+ new' };
    if (kind === 'DELETED') return { sym: '-', cls: 'deleted', text: '- deleted' };
    return { sym: '~', cls: 'changed', text: '~ changed' };
}

// Builds a nested { dirs: Map<name, node>, files: FileSummary[] } tree from the
// flat FileSummary[] list, splitting each path on '/'.
function buildTree(files) {
    const root = { dirs: new Map(), files: [] };
    for (const f of files) {
        const segs = f.path.split('/');
        const fileName = segs.pop();
        let node = root;
        let prefix = '';
        for (const seg of segs) {
            prefix = prefix ? `${prefix}/${seg}` : seg;
            if (!node.dirs.has(seg)) node.dirs.set(seg, { name: seg, fullPath: prefix, dirs: new Map(), files: [] });
            node = node.dirs.get(seg);
        }
        node.files.push(f);
    }
    return root;
}

function renderFileRow(f) {
    const kp = kindPrefix(f.kind);
    const active = state.currentFile === f.path;
    const selected = uiState.selectedFiles.has(f.path);
    const rootPath = rootPathForFile(f);
    const dropdown = renderActionDropdown({
        filePath: f.path,
        optionPath: rootPath,
        state: fileRowState(f, rootPath),
    });
    return `
        <div class="row file-row ${active ? 'active' : ''} ${selected ? 'selected' : ''} ${f.ignored ? 'ignored-hard' : ''}">
            <input type="checkbox" class="row-checkbox" data-select-file-checkbox="${escapeHtml(f.path)}" ${selected ? 'checked' : ''}>
            <span class="row-label" data-select-file="${escapeHtml(f.path)}">${escapeHtml(f.path.split('/').pop())}</span>
            <span class="kind-badge kind-badge--${kp.cls}">${kp.text}</span>
            ${f.stagedCount > 0 ? `<span class="staged-badge">[${f.stagedCount} staged]</span>` : ''}
            ${f.hasUnresolved ? `<span class="unresolved-badge">&#9888; unresolved</span>` : ''}
            ${dropdown}
        </div>`;
}

// Whole-file dropdown state (design D1). resolveRowState covers staged entries
// and ignore rules; f.ignored (server-computed, VALUE-targetValue-aware) is the
// fallback for ignore matches the client can't evaluate without the option's
// current value.
function fileRowState(f, rootPath) {
    const { state: s } = resolveRowState(f.path, rootPath);
    if (s !== 'UNSTAGED') return s;
    return f.ignored ? 'IGNORED' : 'UNSTAGED';
}

// Same derivation as fileRowState, mapped to the legacy action strings the
// bulk dropdown's common/mixed state display expects.
function fileAction(f, rootPath) {
    const s = fileRowState(f, rootPath);
    if (s === 'DEFAULTED') return 'DEFAULT';
    if (s === 'OVERRIDDEN') return 'OVERRIDE';
    if (s === 'IGNORED') return 'IGNORE';
    return null;
}

// Ordered list of file paths as displayed — the flat scope shift-range/Ctrl+A
// gestures are allowed to span, matching renderFileTree/renderDir's own
// traversal order (dirs before files, at each level, both sorted by name).
function collectPaths(node) {
    const dirs = [...node.dirs.values()].sort((a, b) => a.name.localeCompare(b.name));
    const files = [...node.files].sort((a, b) => a.path.localeCompare(b.path));
    const paths = [];
    for (const d of dirs) paths.push(...collectPaths(d));
    paths.push(...files.map(f => f.path));
    return paths;
}
let orderedPaths = [];

// Bulk action bar (design §10 chrome, reused from the main area/staging/history
// panels) — mirrors the per-file dropdown's DEFAULT/OVERRIDE/IGNORE action,
// applied to every selected file's root option (rootPathForFile).
function bulkModeState(paths) {
    const actions = paths.map(p => {
        const f = state.diffFiles.find(x => x.path === p);
        return f ? fileAction(f, rootPathForFile(f)) : null;
    });
    const first = actions[0];
    return actions.every(a => a === first) ? first : 'MIXED';
}

function renderFileTreeBulkBar() {
    const paths = [...uiState.selectedFiles];
    if (paths.length < 2) return '';
    const targets = paths.map(p => {
        const f = state.diffFiles.find(x => x.path === p);
        return { filePath: p, optionPath: f ? rootPathForFile(f) : '$' };
    });
    registerBulkHandler(BULK_ID, (choice) => {
        if (choice === 'IGNORE') requestBulkIgnore(targets);
        else if (choice === 'RESET') requestBulkReset(targets);
        else requestBulkStage(targets, choice);
    });
    const dropdown = renderBulkActionDropdown({
        id: BULK_ID,
        state: bulkModeState(paths),
        options: ['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET'],
    });
    return `<div class="bulk-action-bar">${paths.length} selected ${dropdown}</div>`;
}

function renderDir(node, depth) {
    const isCollapsed = uiState.expandedDirs.has(`collapsed:${node.fullPath}`);
    const childDirs = [...node.dirs.values()].sort((a, b) => a.name.localeCompare(b.name))
        .map(d => renderDir(d, depth + 1)).join('');
    const childFiles = node.files
        .sort((a, b) => a.path.localeCompare(b.path))
        .map(renderFileRow).join('');

    return `
        <div class="dir-header" style="padding-left:${depth * 12}px" data-toggle-dir="${escapeHtml(node.fullPath)}">
            <span>${isCollapsed ? '▶' : '▼'}</span> ${escapeHtml(node.name)}
            <button class="btn-icon dir-ignore-btn" data-ignore-dir="${escapeHtml(node.fullPath)}" title="Ignore directory">&#128683;</button>
        </div>
        <div class="dir-children ${isCollapsed ? 'collapsed' : ''}" style="padding-left:${(depth + 1) * 4}px">
            ${childDirs}${childFiles}
        </div>`;
}

export function renderFileTree() {
    // Filtered mode hides fully-ignored files entirely (flow §9); Greyed mode
    // (default) keeps showing them de-emphasized via .ignored-hard (design §8).
    const visibleFiles = uiState.displayMode === 'FILTERED'
        ? state.diffFiles.filter(f => !f.ignored)
        : state.diffFiles;

    const tree = buildTree(visibleFiles);
    orderedPaths = collectPaths(tree);

    // Prune selected paths that no longer exist (e.g. after a bulk action or a
    // Greyed/Filtered toggle), so the bulk bar/scope logic never sees stale entries.
    for (const path of [...uiState.selectedFiles]) {
        if (!orderedPaths.includes(path)) uiState.selectedFiles.delete(path);
    }

    const rootFiles = tree.files.sort((a, b) => a.path.localeCompare(b.path)).map(renderFileRow).join('');
    const rootDirs = [...tree.dirs.values()].sort((a, b) => a.name.localeCompare(b.name))
        .map(d => renderDir(d, 0)).join('');

    if (visibleFiles.length === 0) {
        return `<div class="empty-state">No changes</div>`;
    }
    return renderFileTreeBulkBar() + rootDirs + rootFiles;
}

function selectFileRow(e, path) {
    uiState.fileAnchor = handleSelectClick({
        event: e,
        key: path,
        orderedKeys: orderedPaths,
        selectedSet: uiState.selectedFiles,
        anchor: uiState.fileAnchor,
    });
}

let initialized = false;
export function initFileTree() {
    if (initialized) return;
    initialized = true;
    const fileTree = document.getElementById('file-tree');

    document.getElementById('file-tree-list').addEventListener('click', async (e) => {
        // Directory-level ignore (design D4): applies a DIRECTORY ignore for
        // the whole directory directly — no type popup, no per-row unignore
        // (an ignored directory disappears from the diff; removal stays in
        // the Ignores popover).
        const ignoreDir = e.target.closest('[data-ignore-dir]');
        if (ignoreDir) {
            e.stopPropagation();
            await api.ignores.add({ filePath: ignoreDir.dataset.ignoreDir, optionPath: '', kind: 'DIRECTORY' });
            await reloadCallback();
            return;
        }
        const dirToggle = e.target.closest('[data-toggle-dir]');
        if (dirToggle) {
            const path = dirToggle.dataset.toggleDir;
            const key = `collapsed:${path}`;
            if (uiState.expandedDirs.has(key)) uiState.expandedDirs.delete(key);
            else uiState.expandedDirs.add(key);
            rerenderCallback();
            return;
        }
        const checkbox = e.target.closest('[data-select-file-checkbox]');
        if (checkbox) {
            selectFileRow(e, checkbox.dataset.selectFileCheckbox);
            // Stop propagation before rerendering, same reason as diff/staging/history:
            // the synchronous innerHTML rebuild detaches e.target from the document.
            e.stopPropagation();
            rerenderCallback();
            return;
        }
        const fileSelect = e.target.closest('[data-select-file]');
        if (fileSelect) {
            selectFileCallback(fileSelect.dataset.selectFile);
        }
    });

    // Click outside a row (but still inside the panel, e.g. a dir header or empty
    // space) deselects everything, except interacting with the bulk action bar
    // itself or an open dropdown menu.
    fileTree.addEventListener('click', (e) => {
        if (uiState.selectedFiles.size > 0
            && !e.target.closest('.row')
            && !e.target.closest('.bulk-action-bar')
            && !e.target.closest('[data-dropdown-root]')) {
            uiState.selectedFiles.clear();
            uiState.fileAnchor = null;
            rerenderCallback();
        }
    });

    // Click outside the panel entirely also deselects.
    document.addEventListener('click', (e) => {
        if (uiState.selectedFiles.size > 0 && !fileTree.contains(e.target)) {
            uiState.selectedFiles.clear();
            uiState.fileAnchor = null;
            rerenderCallback();
        }
    });

    // Tracks which component is "focused" for Ctrl/Cmd+A scoping.
    fileTree.addEventListener('mousedown', () => { uiState.focusedComponent = 'filetree'; });

    document.addEventListener('keydown', (e) => {
        if (uiState.focusedComponent !== 'filetree' || !isSelectAllShortcut(e)) return;
        if (orderedPaths.length === 0) return;
        e.preventDefault();
        selectAll(uiState.selectedFiles, orderedPaths);
        uiState.fileAnchor = orderedPaths[0] ?? null;
        rerenderCallback();
    });
}
