// Generic multi-selection gesture logic (flow §6 "standards web"), shared by the
// main area diff tree and the staging panel. Pure functions operating on a
// `Set<string>` of selected keys + an "anchor" key used for shift-range — no DOM
// here, callers own rendering and event wiring.

// Click on checkbox, no modifiers: select only this row (clears the rest).
export function selectOnly(selectedSet, key) {
    selectedSet.clear();
    selectedSet.add(key);
}

// Ctrl/Cmd+click: toggle this row without touching the rest of the selection.
export function toggleInSet(selectedSet, key) {
    if (selectedSet.has(key)) selectedSet.delete(key);
    else selectedSet.add(key);
}

// Shift+click: range-select from `fromKey` (the anchor) to `toKey`, inclusive,
// within `orderedKeys` — replaces the current selection with that range.
export function selectRange(selectedSet, orderedKeys, fromKey, toKey) {
    const i1 = orderedKeys.indexOf(fromKey);
    const i2 = orderedKeys.indexOf(toKey);
    if (i1 === -1 || i2 === -1) { selectOnly(selectedSet, toKey); return; }
    const [lo, hi] = i1 <= i2 ? [i1, i2] : [i2, i1];
    selectedSet.clear();
    for (let i = lo; i <= hi; i++) selectedSet.add(orderedKeys[i]);
}

// Dispatches a click on a row's selection checkbox to the right gesture
// (flow §6 table). `orderedKeys` is the ordered list of keys the range/select-all
// gestures are allowed to span (the "scope" — e.g. same level + same parent node
// for the main area, the whole list for staging). Returns the new anchor key,
// which the caller must persist for subsequent shift-click chains.
export function handleSelectClick({ event, key, orderedKeys, selectedSet, anchor }) {
    if (event.shiftKey && anchor != null && orderedKeys.includes(anchor)) {
        selectRange(selectedSet, orderedKeys, anchor, key);
        return anchor; // anchor unchanged across a shift-click chain
    }
    if (event.ctrlKey || event.metaKey) {
        toggleInSet(selectedSet, key);
        return key;
    }
    selectOnly(selectedSet, key);
    return key;
}

// Ctrl/Cmd+A — select every key in `orderedKeys`. Caller is responsible for
// checking which component is focused before invoking this.
export function selectAll(selectedSet, orderedKeys) {
    selectedSet.clear();
    for (const k of orderedKeys) selectedSet.add(k);
}

export function isSelectAllShortcut(event) {
    return (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a';
}
