// Shim du store Zustand pour les tests legacy (state.js proxy sur
// window.__moc depuis le change modernize-web-frontend). À importer en PREMIER
// dans tout test legacy qui touche state/uiState.

const storeState = {
    data: {
        recomp: null,
        diffFiles: [],
        currentFile: null,
        currentTree: [],
        draftEntries: [],
        patches: [],
        viewedPatch: null,
        ignores: { entries: [], directories: [] },
        recompIgnores: [],
    },
    ui: {
        expandedDirs: new Set(),
        expandedNodes: new Set(),
        selectedNodes: new Set(),
        selectionAnchor: null,
        selectedStaging: new Set(),
        stagingAnchor: null,
        selectedPatches: new Set(),
        patchAnchor: null,
        selectedFiles: new Set(),
        fileAnchor: null,
        focusedComponent: null,
        rawNodes: new Set(),
        openDropdown: null,
        displayMode: 'GREYED',
        ignoresPopoverOpen: false,
        recompIgnoresPopoverOpen: false,
        ignoresFilterKind: null,
        ignoresSearch: '',
        breadcrumbPath: null,
        focusRequest: null,
    },
};

globalThis.__moc = {
    getState: () => storeState,
    setState: (fn) => {
        Object.assign(storeState, typeof fn === 'function' ? fn(storeState) : fn);
    },
    subscribe: () => () => {},
};
