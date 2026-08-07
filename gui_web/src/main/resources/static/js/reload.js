// Data-reload helpers for diff files / draft entries (tech §6 app.js), extracted
// into a pure module so the refreshRecomp de-duplication logic can be unit
// tested without pulling in app.js's DOM-bound orchestration and side effects.
//
// refreshRecomp=true refreshes state.recomp before branching on mode: several WS
// events (e.g. diff_changed, patches_changed, recomp_changed) are broadcast
// together when a session finalizes/cancels, and their handlers run
// concurrently — reusing a stale cached state.recomp here would race against
// the handler that nulls it, firing recomp-scoped requests against a session
// the backend already tore down. loadAll() passes false since it already just
// refreshed state.recomp itself.

export function makeReloadDiffFiles({ api, state, currentMode }) {
    return async function reloadDiffFiles(refreshRecomp = true) {
        if (refreshRecomp) state.recomp = await api.recomp.get();
        const mode = currentMode();
        state.diffFiles = mode === 'NEW_PATCH' ? (await api.diff.files()).files : (await api.recomp.diff.files()).files;
    };
}

export function makeReloadDraft({ api, state, currentMode }) {
    return async function reloadDraft(refreshRecomp = true) {
        if (refreshRecomp) state.recomp = await api.recomp.get();
        const mode = currentMode();
        state.draftEntries = mode === 'NEW_PATCH' ? (await api.draft.get()).entries : (await api.recomp.entries.get()).entries;
    };
}
