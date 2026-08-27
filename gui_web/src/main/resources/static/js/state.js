// Adaptateur de coexistence (change modernize-web-frontend, design D3) :
// state/uiState sont désormais des VUES sur le store Zustand exposé par le
// bundle React (window.__moc, posé par /assets/mount.js chargé avant ce
// module dans index.html).
//
// - Lecture : piégée vers store.getState().data / .ui.
// - Écriture : mutation SILENCIEUSE in-place (pas de setState) — le legacy
//   suppose des mutations non-notifiantes suivies d'un rerender() explicite.
//   Notifier ici déclencherait un render synchrone entre mousedown et click
//   (ex. focusedComponent) et détacherait la cible du clic avant dispatch.
//   Les actions du store (setUi, loadDiffFile, …), elles, notifient.
// - Les Sets (expandedDirs, selectedNodes, ...) sont partagés par référence :
//   les mutations in-place (add/delete) restent possibles pour le legacy.

// globalThis et pas window : les tests legacy tournent en environnement node.
const store = () => globalThis.__moc;

function forward(slice) {
    return new Proxy({}, {
        get: (_, k) => store().getState()[slice][k],
        set: (_, k, v) => { store().getState()[slice][k] = v; return true; },
    });
}

export const state = forward('data');
export const uiState = forward('ui');

export function currentMode() {
    if (!state.recomp) return 'NEW_PATCH';
    return state.recomp.isAmend ? 'AMEND' : 'RECOMPOSITION';
}

// Name of the patch being amended (AMEND mode targets a single-patch range,
// rangeStart === rangeEnd), or null outside of AMEND mode / before patches load.
export function amendTargetName() {
    if (currentMode() !== 'AMEND' || !state.recomp) return null;
    return state.patches[state.recomp.rangeStart]?.name ?? null;
}

// { start, end } patch names of the active RECOMPOSITION session's range
// (start === end for a single-patch range), or null outside of RECOMPOSITION
// mode / before patches load.
export function recompRangeNames() {
    if (currentMode() !== 'RECOMPOSITION' || !state.recomp) return null;
    const start = state.patches[state.recomp.rangeStart]?.name;
    const end = state.patches[state.recomp.rangeEnd]?.name;
    if (!start || !end) return null;
    return { start, end };
}
