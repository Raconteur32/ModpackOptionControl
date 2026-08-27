// Port TS de selection.js — gestes de multi-sélection génériques (flow §6),
// fonctions pures sur un Set<string> + ancre shift. Aucun DOM ici.

// Click sans modificateur : ne sélectionner que cette ligne.
export function selectOnly(selectedSet: Set<string>, key: string): void {
  selectedSet.clear()
  selectedSet.add(key)
}

// Ctrl/Cmd+click : toggle sans toucher au reste.
export function toggleInSet(selectedSet: Set<string>, key: string): void {
  if (selectedSet.has(key)) selectedSet.delete(key)
  else selectedSet.add(key)
}

// Shift+click : range de `fromKey` (ancre) à `toKey` inclus dans `orderedKeys`
// — remplace la sélection courante.
export function selectRange(selectedSet: Set<string>, orderedKeys: string[], fromKey: string, toKey: string): void {
  const i1 = orderedKeys.indexOf(fromKey)
  const i2 = orderedKeys.indexOf(toKey)
  if (i1 === -1 || i2 === -1) { selectOnly(selectedSet, toKey); return }
  const [lo, hi] = i1 <= i2 ? [i1, i2] : [i2, i1]
  selectedSet.clear()
  for (let i = lo; i <= hi; i++) selectedSet.add(orderedKeys[i])
}

export interface SelectClickEvent { shiftKey: boolean; ctrlKey: boolean; metaKey: boolean }

// Dispatch d'un click sur la checkbox d'une ligne vers le bon geste (flow §6).
// `orderedKeys` = scope ordonné autorisé pour range/select-all. Retourne la
// nouvelle ancre, à persister par l'appelant pour les chaînes de shift-click.
export function handleSelectClick({ event, key, orderedKeys, selectedSet, anchor }: {
  event: SelectClickEvent
  key: string
  orderedKeys: string[]
  selectedSet: Set<string>
  anchor: string | null
}): string {
  if (event.shiftKey && anchor != null && orderedKeys.includes(anchor)) {
    selectRange(selectedSet, orderedKeys, anchor, key)
    return anchor // ancre inchangée pendant une chaîne de shift-click
  }
  if (event.ctrlKey || event.metaKey) {
    toggleInSet(selectedSet, key)
    return key
  }
  selectOnly(selectedSet, key)
  return key
}

// Ctrl/Cmd+A — sélectionne toutes les clés de `orderedKeys`.
export function selectAll(selectedSet: Set<string>, orderedKeys: string[]): void {
  selectedSet.clear()
  for (const k of orderedKeys) selectedSet.add(k)
}

// Index de sélection d'un arbre de diff (construit au render) : scopeOf donne
// le scope d'une clé, siblingsByScope les clés ordonnées d'un scope.
export interface SelectionIndex {
  scopeOf: Map<string, string>
  siblingsByScope: Map<string, string[]>
}

// Résout les clés qu'un Ctrl/Cmd+A doit sélectionner dans l'arbre de diff.
// Sans sélection : les ENFANTS de la racine — jamais la racine seule (le geste
// le plus rapide = "chaque option top-level changée, individuellement"). Une
// racine feuille (fichier supprimé) retombe sur le scope depth-0 (elle-même).
export function selectAllKeys(selIndex: SelectionIndex, filePath: string, anchorKey: string | null): string[] {
  let scope: string | undefined
  if (anchorKey) {
    scope = selIndex.scopeOf.get(anchorKey)
  } else {
    const rootKey = selIndex.siblingsByScope.get(`ROOT::${filePath}`)?.[0]
    scope = rootKey && selIndex.siblingsByScope.has(rootKey) ? rootKey : `ROOT::${filePath}`
  }
  return scope ? (selIndex.siblingsByScope.get(scope) ?? []) : []
}

export function isSelectAllShortcut(event: { ctrlKey: boolean; metaKey: boolean; key: string }): boolean {
  return (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'a'
}
