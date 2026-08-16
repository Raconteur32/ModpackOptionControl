// Frontend-local state (tech §6 state.js).

export const state = {
    // Mode dérivé de recomp (null = NEW_PATCH)
    recomp: null,           // réponse de GET /api/recomp, ou null

    // File tree
    diffFiles: [],          // FileSummary[] du mode courant

    // Main area
    currentFile: null,      // chemin du fichier sélectionné
    currentTree: [],        // DiffNode[] du fichier sélectionné

    // Staging
    draftEntries: [],       // DraftEntry[] du mode courant

    // Patch history
    patches: [],            // { name, entryCount }[]
    viewedPatch: null,      // { name, entries: DraftEntryDto[] } while [View] is active, else null

    // Ignores
    ignores: { entries: [], directories: [] },
    recompIgnores: [],      // { filePath, optionPath }[] from GET /api/ignores/recomp
};

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

// UI-only state, not part of the documented data schema but kept alongside it since
// it also needs to survive re-renders triggered by WS events.
export const uiState = {
    expandedDirs: new Set(),     // directory prefixes expanded in the file tree
    expandedNodes: new Set(),    // DiffNode paths expanded in the main area
    selectedNodes: new Set(),    // "filePath::optionPath" keys checked in the main area
    selectionAnchor: null,       // last key clicked in the main area, for shift-range
    selectedStaging: new Set(),  // "filePath::optionPath" keys checked in the staging panel
    stagingAnchor: null,         // last key clicked in the staging panel, for shift-range
    selectedPatches: new Set(),  // patch names checked in the patch history
    patchAnchor: null,           // last patch name clicked in the patch history, for shift-range
    selectedFiles: new Set(),    // file paths checked in the file tree
    fileAnchor: null,            // last file path clicked in the file tree, for shift-range
    focusedComponent: null,      // 'main' | 'staging' | 'history' | 'filetree' | null — which component Ctrl/Cmd+A applies to
    rawNodes: new Set(),         // DiffNode paths showing [RAW] value
    openDropdown: null,          // { id, up } of the currently open ActionDropdown, or null

    // Ignores (tech/flow §9)
    displayMode: 'GREYED',       // 'GREYED' | 'FILTERED' — how ignored entries are displayed
    ignoresPopoverOpen: false,       // whether the [Ignores N▾] popover is open
    recompIgnoresPopoverOpen: false, // whether the [Recomp ignores ▾] popover is open
    ignoresFilterKind: null,     // null (All) | 'SESSION' | 'VALUE' | 'PERMANENT' | 'DIRECTORY'
    ignoresSearch: '',           // search text filtering the Ignores popover list by path
};
