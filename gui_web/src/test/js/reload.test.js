import { describe, expect, it, vi } from 'vitest';
import { makeReloadDiffFiles, makeReloadDraft } from '../../main/resources/static/js/reload.js';

// Mirrors state.js's currentMode(): null = NEW_PATCH, otherwise driven by isAmend.
function currentModeOf(state) {
    if (!state.recomp) return 'NEW_PATCH';
    return state.recomp.isAmend ? 'AMEND' : 'RECOMPOSITION';
}

function makeApi() {
    return {
        recomp: {
            get: vi.fn().mockResolvedValue(null), // no active session by default
            diff: { files: vi.fn().mockResolvedValue({ files: ['recomp-file'] }) },
            entries: { get: vi.fn().mockResolvedValue({ entries: ['recomp-entry'] }) },
        },
        diff: { files: vi.fn().mockResolvedValue({ files: ['new-patch-file'] }) },
        draft: { get: vi.fn().mockResolvedValue({ entries: ['new-patch-entry'] }) },
    };
}

describe('reloadDiffFiles', () => {
    it('refreshes state.recomp by default before branching on mode', async () => {
        const api = makeApi();
        const state = { recomp: null, diffFiles: [] };
        const reloadDiffFiles = makeReloadDiffFiles({ api, state, currentMode: () => currentModeOf(state) });

        await reloadDiffFiles();

        expect(api.recomp.get).toHaveBeenCalledTimes(1);
        expect(state.diffFiles).toEqual(['new-patch-file']);
    });

    it('skips the recomp refetch when refreshRecomp=false, reusing the caller-provided state.recomp', async () => {
        const api = makeApi();
        const state = { recomp: { isAmend: true }, diffFiles: [] };
        const reloadDiffFiles = makeReloadDiffFiles({ api, state, currentMode: () => currentModeOf(state) });

        await reloadDiffFiles(false);

        expect(api.recomp.get).not.toHaveBeenCalled();
        expect(api.recomp.diff.files).toHaveBeenCalledTimes(1);
        expect(state.diffFiles).toEqual(['recomp-file']);
    });

    it('fetches the recomp-scoped endpoint once state.recomp reflects an active session', async () => {
        const api = makeApi();
        api.recomp.get.mockResolvedValue({ isAmend: true, rangeStart: 0, rangeEnd: 0 });
        const state = { recomp: null, diffFiles: [] };
        const reloadDiffFiles = makeReloadDiffFiles({ api, state, currentMode: () => currentModeOf(state) });

        await reloadDiffFiles();

        expect(api.recomp.diff.files).toHaveBeenCalledTimes(1);
        expect(api.diff.files).not.toHaveBeenCalled();
    });
});

describe('reloadDraft', () => {
    it('refreshes state.recomp by default before branching on mode', async () => {
        const api = makeApi();
        const state = { recomp: null, draftEntries: [] };
        const reloadDraft = makeReloadDraft({ api, state, currentMode: () => currentModeOf(state) });

        await reloadDraft();

        expect(api.recomp.get).toHaveBeenCalledTimes(1);
        expect(state.draftEntries).toEqual(['new-patch-entry']);
    });

    it('skips the recomp refetch when refreshRecomp=false, reusing the caller-provided state.recomp', async () => {
        const api = makeApi();
        const state = { recomp: { isAmend: true }, draftEntries: [] };
        const reloadDraft = makeReloadDraft({ api, state, currentMode: () => currentModeOf(state) });

        await reloadDraft(false);

        expect(api.recomp.get).not.toHaveBeenCalled();
        expect(api.recomp.entries.get).toHaveBeenCalledTimes(1);
        expect(state.draftEntries).toEqual(['recomp-entry']);
    });
});
