// Core staging/ignore business logic shared by the file tree, main area diff tree
// and staging panel. Implements the client-side conflict detection described in
// tech §6 "Détection locale des conflits" and the confirmation popup format of
// flow §5.

import { api } from './api.js';
import { state, currentMode } from './state.js';
import { isDescendant } from './pathutils.js';
import { showConfirmDialog } from './dialogs.js';
import { showIgnoreTypeDialog, parentDirOf } from './ignores.js';

let reloadCallback = async () => {};
export function setReloadCallback(fn) { reloadCallback = fn; }

function backing() {
    return currentMode() === 'NEW_PATCH'
        ? { add: api.draft.add, remove: api.draft.remove }
        : { add: api.recomp.entries.add, remove: api.recomp.entries.remove };
}

export function findDraftEntry(filePath, optionPath) {
    return state.draftEntries.find(e => e.filePath === filePath && e.optionPath === optionPath) ?? null;
}

export function findIgnoreEntry(filePath, optionPath) {
    return state.ignores.entries.find(e => e.filePath === filePath && e.optionPath === optionPath) ?? null;
}

function capitalize(s) { return s.length ? s[0] + s.slice(1).toLowerCase() : s; }

function fileLabel(filePath, optionPath) {
    return optionPath ? `${filePath} › ${optionPath}` : filePath;
}

// Builds the list of confirmation effects for staging `optionPath` with `mode`,
// and the list of draft entries that must be removed first to apply them.
function planStage(filePath, optionPath, mode) {
    const effects = [];
    const toRemove = [];

    const existingIgnore = findIgnoreEntry(filePath, optionPath);
    if (existingIgnore) {
        effects.push({
            title: fileLabel(filePath, optionPath),
            detail: `Will be un-ignored (active ${capitalize(existingIgnore.kind)} ignore)`,
        });
    }

    for (const entry of state.draftEntries) {
        if (entry.filePath !== filePath) continue;
        if (entry.optionPath === optionPath) continue; // exact overwrite, no warning needed
        if (isDescendant(entry.optionPath, optionPath)) {
            effects.push({
                title: fileLabel(filePath, optionPath),
                detail: `Will replace child entry\n${entry.optionPath} [${entry.mode}]`,
            });
            toRemove.push(entry);
        } else if (isDescendant(optionPath, entry.optionPath)) {
            effects.push({
                title: fileLabel(filePath, optionPath),
                detail: `Will replace parent entry\n${entry.optionPath} [${entry.mode}]`,
            });
            toRemove.push(entry);
        }
    }

    return { effects, toRemove, existingIgnore };
}

async function executeStage(filePath, optionPath, mode, toRemove, existingIgnore) {
    const b = backing();
    for (const entry of toRemove) {
        await b.remove({ filePath: entry.filePath, optionPath: entry.optionPath });
    }
    if (existingIgnore) {
        await api.ignores.remove({ filePath, optionPath, kind: existingIgnore.kind });
    }
    await b.add({ filePath, optionPath, mode });
    await reloadCallback();
}

export function requestStage(filePath, optionPath, mode) {
    const { effects, toRemove, existingIgnore } = planStage(filePath, optionPath, mode);
    if (effects.length > 0) {
        showConfirmDialog({
            title: 'Confirm action',
            effects,
            onConfirm: () => executeStage(filePath, optionPath, mode, toRemove, existingIgnore),
        });
    } else {
        executeStage(filePath, optionPath, mode, toRemove, existingIgnore);
    }
}

// Applies the chosen ignore kind (flow §9 ignore-type popup). DIRECTORY has no
// per-option semantics — it targets the file's parent directory as a whole via
// MocSettings.ignoredPaths, so filePath/optionPath are remapped accordingly
// before calling the API (matches the {filePath, optionPath: '', kind, ...}
// contract asserted by IgnoreRoutesTest.kt).
//
// In AMEND/RECOMPOSITION mode, non-DIRECTORY kinds go through
// /api/recomp/entries instead of the general /api/ignores: this both scopes
// the ignore to IgnoreStore.recompositionIgnores (cleared with the session)
// and resolves the entry's inter-patch conflict, if any.
async function executeIgnore(filePath, optionPath, existingDraftEntry, kind, targetValue) {
    if (existingDraftEntry) {
        await backing().remove({ filePath, optionPath });
    }
    if (kind === 'DIRECTORY') {
        await api.ignores.add({ filePath: parentDirOf(filePath), optionPath: '', kind });
    } else if (currentMode() !== 'NEW_PATCH') {
        await api.recomp.entries.add({ filePath, optionPath, action: 'ignore', kind });
    } else {
        await api.ignores.add({ filePath, optionPath, kind, targetValue });
    }
    await reloadCallback();
}

// Choosing IGNORE from the dropdown opens the ignore-type popup (flow §9). If
// the entry was already staged, the staging-conflict confirmation popup (flow
// §5) is shown first and the type popup only opens once it's confirmed.
export function requestIgnore(filePath, optionPath) {
    const existingDraftEntry = findDraftEntry(filePath, optionPath);

    const openTypeDialog = () => {
        showIgnoreTypeDialog({
            filePath,
            optionPath,
            onConfirm: (kind, targetValue) => executeIgnore(filePath, optionPath, existingDraftEntry, kind, targetValue),
        });
    };

    if (existingDraftEntry) {
        showConfirmDialog({
            title: 'Confirm action',
            effects: [{
                title: fileLabel(filePath, optionPath),
                detail: `Will remove staged entry [${existingDraftEntry.mode}]`,
            }],
            onConfirm: openTypeDialog,
        });
    } else {
        openTypeDialog();
    }
}

// Removing an entry from staging (the "x" button) — no confirmation (flow §11).
export async function removeDraftEntryDirect(filePath, optionPath) {
    await backing().remove({ filePath, optionPath });
    await reloadCallback();
}

// Removing several entries from staging at once (staging bulk bar [Remove],
// flow §11) — no confirmation, same as the single "x" button.
export async function removeDraftEntriesDirect(targets) {
    const b = backing();
    for (const { filePath, optionPath } of targets) {
        await b.remove({ filePath, optionPath });
    }
    await reloadCallback();
}

export function rootPathForFile(file) {
    return file.kind === 'DELETED' ? '' : '$';
}

// ---------- Mass actions (flow §5 "y compris une action de masse" / §8) ----------
//
// All warnings produced by the whole selection are collected and presented in a
// single confirmation popup, reusing the same planStage/planIgnore + showConfirmDialog
// pipeline as the single-entry path above rather than duplicating it.

// Aggregates planStage() across every target, de-duplicating draft entries that
// would otherwise be removed twice (possible when two selected siblings both
// happen to be a descendant/ancestor of the same pre-existing draft entry).
function planBulkStage(targets, mode) {
    const effects = [];
    const toRemove = [];
    const removeKeys = new Set();
    const ignoreRemovals = [];

    for (const { filePath, optionPath } of targets) {
        const { effects: e, toRemove: tr, existingIgnore } = planStage(filePath, optionPath, mode);
        effects.push(...e);
        for (const entry of tr) {
            const k = `${entry.filePath}::${entry.optionPath}`;
            if (!removeKeys.has(k)) { removeKeys.add(k); toRemove.push(entry); }
        }
        if (existingIgnore) ignoreRemovals.push({ filePath, optionPath, kind: existingIgnore.kind });
    }
    return { effects, toRemove, ignoreRemovals };
}

async function executeBulkStage(targets, mode, toRemove, ignoreRemovals) {
    const b = backing();
    for (const entry of toRemove) {
        await b.remove({ filePath: entry.filePath, optionPath: entry.optionPath });
    }
    for (const ig of ignoreRemovals) {
        await api.ignores.remove(ig);
    }
    for (const { filePath, optionPath } of targets) {
        await b.add({ filePath, optionPath, mode });
    }
    await reloadCallback();
}

// targets: [{ filePath, optionPath }] — the whole selection. Applies `mode`
// (DEFAULT/OVERRIDE) to all of them behind one confirmation popup (flow §8).
export function requestBulkStage(targets, mode) {
    const { effects, toRemove, ignoreRemovals } = planBulkStage(targets, mode);
    if (effects.length > 0) {
        showConfirmDialog({
            title: 'Confirm action',
            effects,
            onConfirm: () => executeBulkStage(targets, mode, toRemove, ignoreRemovals),
        });
    } else {
        executeBulkStage(targets, mode, toRemove, ignoreRemovals);
    }
}

function planBulkIgnore(targets) {
    const effects = [];
    const existingEntries = [];
    for (const { filePath, optionPath } of targets) {
        const existingDraftEntry = findDraftEntry(filePath, optionPath);
        if (existingDraftEntry) {
            effects.push({
                title: fileLabel(filePath, optionPath),
                detail: `Will remove staged entry [${existingDraftEntry.mode}]`,
            });
            existingEntries.push({ filePath, optionPath });
        }
    }
    return { effects, existingEntries };
}

// Applies `kind` (and `targetValue`, for VALUE) to every target from one
// ignore-type popup choice. Same simplification as a single-target VALUE
// ignore, applied uniformly to the whole selection — a single manually-typed
// targetValue rather than per-target current-value prefill (deviation: see
// Phase 5 report, kept in scope for simplicity since bulk-ignoring distinct
// values under one shared target would rarely be meaningful anyway).
async function executeBulkIgnore(targets, existingEntries, kind, targetValue) {
    const b = backing();
    for (const e of existingEntries) {
        await b.remove(e);
    }
    for (const { filePath, optionPath } of targets) {
        if (kind === 'DIRECTORY') {
            await api.ignores.add({ filePath: parentDirOf(filePath), optionPath: '', kind });
        } else if (currentMode() !== 'NEW_PATCH') {
            await api.recomp.entries.add({ filePath, optionPath, action: 'ignore', kind });
        } else {
            await api.ignores.add({ filePath, optionPath, kind, targetValue });
        }
    }
    await reloadCallback();
}

// Bulk IGNORE (flow §5 "y compris une action de masse", §9 ignore-type popup)
// — same staging-conflict-then-type-popup sequencing as the single-entry path.
export function requestBulkIgnore(targets) {
    const { effects, existingEntries } = planBulkIgnore(targets);

    const openTypeDialog = () => {
        showIgnoreTypeDialog({
            filePath: targets[0]?.filePath ?? '',
            optionPath: targets[0]?.optionPath ?? '',
            onConfirm: (kind, targetValue) => executeBulkIgnore(targets, existingEntries, kind, targetValue),
        });
    };

    if (effects.length > 0) {
        showConfirmDialog({
            title: 'Confirm action',
            effects,
            onConfirm: openTypeDialog,
        });
    } else {
        openTypeDialog();
    }
}

// Staging bulk mode dropdown (flow §11) — entries are already staged, so this is
// a plain mode switch with no conflict/ignore checks and no confirmation, same
// as picking DEFAULT/OVERRIDE on a single staged row's dropdown.
export async function setDraftEntriesMode(targets, mode) {
    const b = backing();
    for (const { filePath, optionPath } of targets) {
        await b.add({ filePath, optionPath, mode });
    }
    await reloadCallback();
}
