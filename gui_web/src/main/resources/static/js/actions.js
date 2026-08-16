// Core staging/ignore business logic shared by the file tree, main area diff tree
// and staging panel. Implements the client-side conflict detection described in
// tech §6 "Détection locale des conflits" and the confirmation popup format of
// flow §5.

import { api } from './api.js';
import { state, currentMode } from './state.js';
import { isDescendant } from './pathutils.js';
import { showConfirmDialog } from './dialogs.js';
import { showIgnoreTypeDialog } from './ignores.js';

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

// Client-side replica of the server's matchesTargetValue (Diffs.kt): a VALUE
// ignore only applies while the option's current new value matches its
// targetValue, with a numeric-aware fallback ("1" vs "1.0"). Callers that
// don't have the value at hand (the file tree) pass undefined and get a
// conservative match — an inert rule can be RESET away harmlessly.
function matchesTargetValue(newValue, targetValue) {
    if (targetValue == null) return false;
    if (newValue === undefined) return true;
    if (newValue === null) return false;
    if (String(newValue) === targetValue) return true;
    const a = Number(newValue), b = Number(targetValue);
    return Number.isFinite(a) && Number.isFinite(b) && a === b;
}

// Single source of truth for a row's dropdown state (design D1):
// UNSTAGED | DEFAULTED | OVERRIDDEN | IGNORED (+ ignoreKind).
export function resolveRowState(filePath, optionPath, newValue) {
    const entry = findDraftEntry(filePath, optionPath);
    if (entry) return { state: entry.mode === 'DEFAULT' ? 'DEFAULTED' : 'OVERRIDDEN' };
    const ig = findIgnoreEntry(filePath, optionPath);
    if (ig && (ig.kind !== 'VALUE' || matchesTargetValue(newValue, ig.targetValue))) {
        return { state: 'IGNORED', ignoreKind: ig.kind };
    }
    if (state.recompIgnores.some(e => e.filePath === filePath && e.optionPath === optionPath)) {
        return { state: 'IGNORED', ignoreKind: 'RECOMP' };
    }
    return { state: 'UNSTAGED' };
}

// RESET (design D2): unified inverse action returning a row to UNSTAGED —
// removes the staged entry or the matching ignore rule, no confirmation.
export async function requestReset(filePath, optionPath) {
    const { state: rowState, ignoreKind } = resolveRowState(filePath, optionPath);
    if (rowState === 'IGNORED') {
        if (ignoreKind === 'RECOMP') await api.ignores.recomp.remove({ filePath, optionPath });
        else if (ignoreKind && ignoreKind !== 'DIRECTORY') await api.ignores.remove({ filePath, optionPath, kind: ignoreKind });
    } else if (rowState !== 'UNSTAGED') {
        await backing().remove({ filePath, optionPath });
    }
    await reloadCallback();
}

// Bulk RESET (design D3): per-row inverse across the whole selection —
// unstages staged rows, unignores ignored rows, skips UNSTAGED rows.
export async function requestBulkReset(targets) {
    const b = backing();
    for (const { filePath, optionPath } of targets) {
        const { state: rowState, ignoreKind } = resolveRowState(filePath, optionPath);
        if (rowState === 'IGNORED') {
            if (ignoreKind === 'RECOMP') await api.ignores.recomp.remove({ filePath, optionPath });
            else if (ignoreKind && ignoreKind !== 'DIRECTORY') await api.ignores.remove({ filePath, optionPath, kind: ignoreKind });
        } else if (rowState !== 'UNSTAGED') {
            await b.remove({ filePath, optionPath });
        }
    }
    await reloadCallback();
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

    // Recomposition-scoped ignores live in their own list (state.recompIgnores)
    // — detect them too so staging over one shows the same un-ignore warning
    // (design D6; the server also removes it atomically as a safety net).
    const hasRecompIgnore = state.recompIgnores.some(e => e.filePath === filePath && e.optionPath === optionPath);
    if (hasRecompIgnore) {
        effects.push({
            title: fileLabel(filePath, optionPath),
            detail: 'Will be un-ignored (recomposition ignore)',
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

    return { effects, toRemove, existingIgnore, hasRecompIgnore };
}

async function executeStage(filePath, optionPath, mode, toRemove, existingIgnore, hasRecompIgnore) {
    const b = backing();
    for (const entry of toRemove) {
        await b.remove({ filePath: entry.filePath, optionPath: entry.optionPath });
    }
    if (existingIgnore) {
        await api.ignores.remove({ filePath, optionPath, kind: existingIgnore.kind });
    }
    if (hasRecompIgnore) {
        await api.ignores.recomp.remove({ filePath, optionPath });
    }
    await b.add({ filePath, optionPath, mode });
    await reloadCallback();
}

export function requestStage(filePath, optionPath, mode) {
    const { effects, toRemove, existingIgnore, hasRecompIgnore } = planStage(filePath, optionPath, mode);
    if (effects.length > 0) {
        showConfirmDialog({
            title: 'Confirm action',
            effects,
            onConfirm: () => executeStage(filePath, optionPath, mode, toRemove, existingIgnore, hasRecompIgnore),
        });
    } else {
        executeStage(filePath, optionPath, mode, toRemove, existingIgnore, hasRecompIgnore);
    }
}

// Applies the chosen ignore kind (flow §9 ignore-type popup). Only SESSION,
// VALUE and PERMANENT reach this function — DIRECTORY ignores are applied from
// the file tree's directory rows (design D4).
//
// In AMEND/RECOMPOSITION mode, kinds go through /api/recomp/entries instead of
// the general /api/ignores: this both scopes the ignore to
// IgnoreStore.recompositionIgnores (cleared with the session) and resolves the
// entry's inter-patch conflict, if any.
async function executeIgnore(filePath, optionPath, existingDraftEntry, kind, targetValue) {
    if (existingDraftEntry) {
        await backing().remove({ filePath, optionPath });
    }
    if (currentMode() !== 'NEW_PATCH') {
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
    const recompIgnoreRemovals = [];

    for (const { filePath, optionPath } of targets) {
        const { effects: e, toRemove: tr, existingIgnore, hasRecompIgnore } = planStage(filePath, optionPath, mode);
        effects.push(...e);
        for (const entry of tr) {
            const k = `${entry.filePath}::${entry.optionPath}`;
            if (!removeKeys.has(k)) { removeKeys.add(k); toRemove.push(entry); }
        }
        if (existingIgnore) ignoreRemovals.push({ filePath, optionPath, kind: existingIgnore.kind });
        if (hasRecompIgnore) recompIgnoreRemovals.push({ filePath, optionPath });
    }
    return { effects, toRemove, ignoreRemovals, recompIgnoreRemovals };
}

async function executeBulkStage(targets, mode, toRemove, ignoreRemovals, recompIgnoreRemovals) {
    const b = backing();
    for (const entry of toRemove) {
        await b.remove({ filePath: entry.filePath, optionPath: entry.optionPath });
    }
    for (const ig of ignoreRemovals) {
        await api.ignores.remove(ig);
    }
    for (const ig of recompIgnoreRemovals) {
        await api.ignores.recomp.remove(ig);
    }
    for (const { filePath, optionPath } of targets) {
        await b.add({ filePath, optionPath, mode });
    }
    await reloadCallback();
}

// targets: [{ filePath, optionPath }] — the whole selection. Applies `mode`
// (DEFAULT/OVERRIDE) to all of them behind one confirmation popup (flow §8).
export function requestBulkStage(targets, mode) {
    const { effects, toRemove, ignoreRemovals, recompIgnoreRemovals } = planBulkStage(targets, mode);
    if (effects.length > 0) {
        showConfirmDialog({
            title: 'Confirm action',
            effects,
            onConfirm: () => executeBulkStage(targets, mode, toRemove, ignoreRemovals, recompIgnoreRemovals),
        });
    } else {
        executeBulkStage(targets, mode, toRemove, ignoreRemovals, recompIgnoreRemovals);
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
        if (currentMode() !== 'NEW_PATCH') {
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
