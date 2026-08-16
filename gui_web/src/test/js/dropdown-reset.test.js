import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { menuItemsFor, displayFor } from '../../main/resources/static/js/dropdown.js';
import { resolveRowState, requestReset, requestBulkReset, setReloadCallback } from '../../main/resources/static/js/actions.js';
import { state } from '../../main/resources/static/js/state.js';

const FILE = 'config/app.json';
const OPT = "$['a']";

function resetState() {
    state.recomp = null;
    state.draftEntries = [];
    state.ignores = { entries: [], directories: [] };
    state.recompIgnores = [];
}

describe('menuItemsFor (state → actions)', () => {
    it('offers the three staging actions when UNSTAGED', () => {
        expect(menuItemsFor('UNSTAGED')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE']);
    });
    it('adds RESET when staged', () => {
        expect(menuItemsFor('DEFAULTED')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET']);
        expect(menuItemsFor('OVERRIDDEN')).toEqual(['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET']);
    });
    it('offers only RESET when IGNORED', () => {
        expect(menuItemsFor('IGNORED')).toEqual(['RESET']);
    });
});

describe('displayFor (state → button label)', () => {
    it('renders UNSTAGED empty', () => {
        expect(displayFor('UNSTAGED')).toEqual({ cls: '', label: '' });
    });
    it('keeps the legacy DEFAULT/OVERRIDE/IGNORE labels', () => {
        expect(displayFor('DEFAULTED').label).toBe('DEFAULT');
        expect(displayFor('OVERRIDDEN').label).toBe('OVERRIDE');
        expect(displayFor('IGNORED').label).toContain('IGNORE');
    });
});

describe('resolveRowState', () => {
    beforeEach(resetState);

    it('maps staged entries to DEFAULTED/OVERRIDDEN', () => {
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT' }];
        expect(resolveRowState(FILE, OPT).state).toBe('DEFAULTED');
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE' }];
        expect(resolveRowState(FILE, OPT).state).toBe('OVERRIDDEN');
    });

    it('maps ignore entries to IGNORED with their kind', () => {
        state.ignores.entries = [{ filePath: FILE, optionPath: OPT, kind: 'SESSION', targetValue: null }];
        expect(resolveRowState(FILE, OPT)).toEqual({ state: 'IGNORED', ignoreKind: 'SESSION' });
    });

    it('applies VALUE ignores only when the current value matches targetValue', () => {
        state.ignores.entries = [{ filePath: FILE, optionPath: OPT, kind: 'VALUE', targetValue: '1' }];
        expect(resolveRowState(FILE, OPT, 1).state).toBe('IGNORED');   // numeric-aware "1" vs 1
        expect(resolveRowState(FILE, OPT, 2).state).toBe('UNSTAGED');
    });

    it('treats VALUE ignores conservatively when the value is unknown', () => {
        state.ignores.entries = [{ filePath: FILE, optionPath: OPT, kind: 'VALUE', targetValue: '1' }];
        expect(resolveRowState(FILE, OPT).state).toBe('IGNORED'); // newValue undefined (file tree)
    });

    it('maps recomposition ignores to IGNORED/RECOMP', () => {
        state.recompIgnores = [{ filePath: FILE, optionPath: OPT }];
        expect(resolveRowState(FILE, OPT)).toEqual({ state: 'IGNORED', ignoreKind: 'RECOMP' });
    });

    it('staged wins over ignored', () => {
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE' }];
        state.recompIgnores = [{ filePath: FILE, optionPath: OPT }];
        expect(resolveRowState(FILE, OPT).state).toBe('OVERRIDDEN');
    });
});

describe('requestReset routing', () => {
    const calls = [];
    const realFetch = globalThis.fetch;

    beforeEach(() => {
        resetState();
        calls.length = 0;
        globalThis.fetch = vi.fn(async (url, opts) => {
            calls.push({ url, method: opts?.method ?? 'GET', body: opts?.body ? JSON.parse(opts.body) : null });
            return { ok: true, status: 200, json: async () => ({}) };
        });
        setReloadCallback(async () => {});
    });
    afterEach(() => { globalThis.fetch = realFetch; });

    it('unstages a staged entry via DELETE /api/draft/entries (NEW_PATCH)', async () => {
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT' }];
        await requestReset(FILE, OPT);
        expect(calls).toEqual([{ url: '/api/draft/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }]);
    });

    it('unignores a session ignore via DELETE /api/ignores with its kind', async () => {
        state.ignores.entries = [{ filePath: FILE, optionPath: OPT, kind: 'SESSION', targetValue: null }];
        await requestReset(FILE, OPT);
        expect(calls).toEqual([{ url: '/api/ignores', method: 'DELETE', body: { filePath: FILE, optionPath: OPT, kind: 'SESSION' } }]);
    });

    it('unignores a recomp ignore via DELETE /api/ignores/recomp', async () => {
        state.recomp = { isAmend: false }; // AMEND/RECOMP mode
        state.recompIgnores = [{ filePath: FILE, optionPath: OPT }];
        await requestReset(FILE, OPT);
        expect(calls).toEqual([{ url: '/api/ignores/recomp', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }]);
    });

    it('unstages via /api/recomp/entries in AMEND/RECOMP mode', async () => {
        state.recomp = { isAmend: true };
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE' }];
        await requestReset(FILE, OPT);
        expect(calls).toEqual([{ url: '/api/recomp/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }]);
    });

    it('does nothing for an UNSTAGED row', async () => {
        await requestReset(FILE, OPT);
        expect(calls).toEqual([]);
    });
});

describe('requestBulkReset', () => {
    const calls = [];
    const realFetch = globalThis.fetch;

    beforeEach(() => {
        resetState();
        calls.length = 0;
        globalThis.fetch = vi.fn(async (url, opts) => {
            calls.push({ url, method: opts?.method ?? 'GET', body: opts?.body ? JSON.parse(opts.body) : null });
            return { ok: true, status: 200, json: async () => ({}) };
        });
        setReloadCallback(async () => {});
    });
    afterEach(() => { globalThis.fetch = realFetch; });

    it('applies the per-row inverse across a mixed selection, skipping UNSTAGED', async () => {
        state.draftEntries = [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT' }];
        state.ignores.entries = [{ filePath: FILE, optionPath: "$['b']", kind: 'PERMANENT', targetValue: null }];
        await requestBulkReset([
            { filePath: FILE, optionPath: OPT },          // staged → unstage
            { filePath: FILE, optionPath: "$['b']" },     // ignored → unignore
            { filePath: FILE, optionPath: "$['c']" },     // unstaged → skip
        ]);
        expect(calls).toEqual([
            { url: '/api/draft/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } },
            { url: '/api/ignores', method: 'DELETE', body: { filePath: FILE, optionPath: "$['b']", kind: 'PERMANENT' } },
        ]);
    });
});
