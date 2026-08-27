// Port TS du plan de staging d'actions.js — détection locale des conflits
// (tech §6) et contenu des popups de confirmation (flow §5). Fonctions pures :
// les données sont injectées, l'exécution (appels API) reste côté store.
import type { DraftEntry, IgnoreEntry } from '../types'
import { findDraftEntry, findIgnoreEntry } from './rowState'

// Sémantique isDescendant de common/.../DiffUtils.kt : chemins du type
// "$.client.maxFps" ou "$['weird key'][0]".
export function isDescendant(childPath: string, parentPath: string): boolean {
  if (childPath.length <= parentPath.length) return false
  if (!childPath.startsWith(parentPath)) return false
  const c = childPath[parentPath.length]
  return c === '.' || c === '['
}

function capitalize(s: string): string {
  return s.length ? s[0] + s.slice(1).toLowerCase() : s
}

function fileLabel(filePath: string, optionPath: string): string {
  return optionPath ? `${filePath} › ${optionPath}` : filePath
}

export interface ConfirmEffect {
  title: string
  detail: string
}

export interface StagePlanData {
  draftEntries: DraftEntry[]
  ignores: IgnoreEntry[]
  recompIgnores: { filePath: string; optionPath: string }[]
}

export interface StagePlan {
  effects: ConfirmEffect[]
  toRemove: DraftEntry[]
  existingIgnore: IgnoreEntry | null
  hasRecompIgnore: boolean
}

// Construit la liste des effets de confirmation pour le staging de
// `optionPath` avec `mode`, et les entrées de draft à retirer d'abord.
export function planStage(data: StagePlanData, filePath: string, optionPath: string, _mode: 'DEFAULT' | 'OVERRIDE'): StagePlan {
  const effects: ConfirmEffect[] = []
  const toRemove: DraftEntry[] = []

  const existingIgnore = findIgnoreEntry(data.ignores, filePath, optionPath)
  if (existingIgnore) {
    effects.push({
      title: fileLabel(filePath, optionPath),
      detail: `Will be un-ignored (active ${capitalize(existingIgnore.kind)} ignore)`,
    })
  }

  // Les ignores de recomposition vivent dans leur propre liste — les détecter
  // aussi pour afficher le même warning d'un-ignore (le serveur les retire
  // aussi atomiquement en filet de sécurité).
  const hasRecompIgnore = data.recompIgnores.some((e) => e.filePath === filePath && e.optionPath === optionPath)
  if (hasRecompIgnore) {
    effects.push({
      title: fileLabel(filePath, optionPath),
      detail: 'Will be un-ignored (recomposition ignore)',
    })
  }

  for (const entry of data.draftEntries) {
    if (entry.filePath !== filePath) continue
    if (entry.optionPath === optionPath) continue // overwrite exact, pas de warning
    if (isDescendant(entry.optionPath, optionPath)) {
      effects.push({
        title: fileLabel(filePath, optionPath),
        detail: `Will replace child entry\n${entry.optionPath} [${entry.mode}]`,
      })
      toRemove.push(entry)
    } else if (isDescendant(optionPath, entry.optionPath)) {
      effects.push({
        title: fileLabel(filePath, optionPath),
        detail: `Will replace parent entry\n${entry.optionPath} [${entry.mode}]`,
      })
      toRemove.push(entry)
    }
  }

  return { effects, toRemove, existingIgnore, hasRecompIgnore }
}

export interface BulkStagePlan {
  effects: ConfirmEffect[]
  toRemove: DraftEntry[]
  ignoreRemovals: { filePath: string; optionPath: string; kind: string }[]
  recompIgnoreRemovals: { filePath: string; optionPath: string }[]
}

// Agrège planStage() sur toute la sélection en dé-dupliquant les entrées à
// retirer (deux siblings sélectionnés peuvent être descendants du même draft).
export function planBulkStage(data: StagePlanData, targets: { filePath: string; optionPath: string }[], mode: 'DEFAULT' | 'OVERRIDE'): BulkStagePlan {
  const effects: ConfirmEffect[] = []
  const toRemove: DraftEntry[] = []
  const removeKeys = new Set<string>()
  const ignoreRemovals: BulkStagePlan['ignoreRemovals'] = []
  const recompIgnoreRemovals: BulkStagePlan['recompIgnoreRemovals'] = []

  for (const { filePath, optionPath } of targets) {
    const { effects: e, toRemove: tr, existingIgnore, hasRecompIgnore } = planStage(data, filePath, optionPath, mode)
    effects.push(...e)
    for (const entry of tr) {
      const k = `${entry.filePath}::${entry.optionPath}`
      if (!removeKeys.has(k)) { removeKeys.add(k); toRemove.push(entry) }
    }
    if (existingIgnore) ignoreRemovals.push({ filePath, optionPath, kind: existingIgnore.kind })
    if (hasRecompIgnore) recompIgnoreRemovals.push({ filePath, optionPath })
  }
  return { effects, toRemove, ignoreRemovals, recompIgnoreRemovals }
}

export interface BulkIgnorePlan {
  effects: ConfirmEffect[]
  existingEntries: { filePath: string; optionPath: string }[]
}

export function planBulkIgnore(data: StagePlanData, targets: { filePath: string; optionPath: string }[]): BulkIgnorePlan {
  const effects: ConfirmEffect[] = []
  const existingEntries: { filePath: string; optionPath: string }[] = []
  for (const { filePath, optionPath } of targets) {
    const existingDraftEntry = findDraftEntry(data.draftEntries, filePath, optionPath)
    if (existingDraftEntry) {
      effects.push({
        title: fileLabel(filePath, optionPath),
        detail: `Will remove staged entry [${existingDraftEntry.mode}]`,
      })
      existingEntries.push({ filePath, optionPath })
    }
  }
  return { effects, existingEntries }
}
