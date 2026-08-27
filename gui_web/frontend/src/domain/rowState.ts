// Port TS des fonctions domaine de dropdown.js/actions.js — état d'une ligne
// (UNSTAGED | DEFAULTED | OVERRIDDEN | IGNORED) et actions du menu par ligne.
// Fonctions pures : les données sont injectées (draftEntries, ignores,
// recompIgnores) au lieu d'être lues depuis un état global.
import type { DraftEntry, IgnoreEntry } from '../types'

export type RowStateName = 'UNSTAGED' | 'DEFAULTED' | 'OVERRIDDEN' | 'IGNORED'
export type RowState = { state: RowStateName; ignoreKind?: string }

export interface RowStateData {
  draftEntries: DraftEntry[]
  ignores: IgnoreEntry[]
  recompIgnores: { filePath: string; optionPath: string }[]
}

export function findDraftEntry(draftEntries: DraftEntry[], filePath: string, optionPath: string): DraftEntry | null {
  return draftEntries.find((e) => e.filePath === filePath && e.optionPath === optionPath) ?? null
}

export function findIgnoreEntry(ignores: IgnoreEntry[], filePath: string, optionPath: string): IgnoreEntry | null {
  return ignores.find((e) => e.filePath === filePath && e.optionPath === optionPath) ?? null
}

// Réplique client du matchesTargetValue serveur (Diffs.kt) : un ignore VALUE ne
// s'applique que tant que la nouvelle valeur courante matche targetValue, avec
// fallback numérique ("1" vs "1.0"). Sans valeur sous la main (file tree) on
// matche conservativement — une règle inerte peut être RESET sans mal.
export function matchesTargetValue(newValue: unknown, targetValue: string | null | undefined): boolean {
  if (targetValue == null) return false
  if (newValue === undefined) return true
  if (newValue === null) return false
  if (String(newValue) === targetValue) return true
  const a = Number(newValue)
  const b = Number(targetValue)
  return Number.isFinite(a) && Number.isFinite(b) && a === b
}

// Source de vérité unique de l'état d'une ligne (design D1 des changes
// dropdown-reset-action / unify-root-option-node).
export function resolveRowState(data: RowStateData, filePath: string, optionPath: string, newValue?: unknown): RowState {
  const entry = findDraftEntry(data.draftEntries, filePath, optionPath)
  if (entry) return { state: entry.mode === 'DEFAULT' ? 'DEFAULTED' : 'OVERRIDDEN' }
  const ig = findIgnoreEntry(data.ignores, filePath, optionPath)
  if (ig && (ig.kind !== 'VALUE' || matchesTargetValue(newValue, ig.targetValue))) {
    return { state: 'IGNORED', ignoreKind: ig.kind }
  }
  if (data.recompIgnores.some((e) => e.filePath === filePath && e.optionPath === optionPath)) {
    return { state: 'IGNORED', ignoreKind: 'RECOMP' }
  }
  return { state: 'UNSTAGED' }
}

export type RowAction = 'DEFAULT' | 'OVERRIDE' | 'IGNORE' | 'RESET'

// Actions offertes par état : UNSTAGED → stage/ignore ; staged → idem + RESET ;
// IGNORED → RESET seul.
export function menuItemsFor(state: RowStateName): RowAction[] {
  if (state === 'IGNORED') return ['RESET']
  if (state === 'DEFAULTED' || state === 'OVERRIDDEN') return ['DEFAULT', 'OVERRIDE', 'IGNORE', 'RESET']
  return ['DEFAULT', 'OVERRIDE', 'IGNORE']
}

// Label affiché sur le bouton d'état (UNSTAGED = vide).
export function displayFor(state: RowStateName): { cls: string; label: string } {
  if (state === 'DEFAULTED') return { cls: 'mode-DEFAULT', label: 'DEFAULT' }
  if (state === 'OVERRIDDEN') return { cls: 'mode-OVERRIDE', label: 'OVERRIDE' }
  if (state === 'IGNORED') return { cls: 'mode-IGNORE', label: 'IGNORE 🚫' }
  return { cls: '', label: '' }
}
