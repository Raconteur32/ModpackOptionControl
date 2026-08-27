// DTOs mirroring gui_web Models.kt + route payloads (Gson omits nulls).
export interface DiffNode {
  path: string
  label: string
  kind: 'CHANGED' | 'NEW' | 'DELETED'
  oldValue?: unknown
  newValue?: unknown
  hasChildren: boolean
  children: DiffNode[]
  action?: 'DEFAULT' | 'OVERRIDE' | 'IGNORE'
  ignoreKind?: string
  unresolved?: boolean
  source?: string
}

export interface FileSummary {
  path: string
  kind: 'CHANGED' | 'NEW' | 'DELETED'
  stagedCount: number
  hasUnresolved: boolean
  ignored: boolean
}

export interface DraftEntry {
  filePath: string
  optionPath: string
  mode: 'DEFAULT' | 'OVERRIDE'
  kind: 'VALUE' | 'DELETION'
  source?: string
}

export interface IgnoreEntry {
  filePath: string
  optionPath: string
  kind: 'SESSION' | 'VALUE' | 'PERMANENT'
  targetValue?: string | null
}

export interface Ignores {
  entries: IgnoreEntry[]
  directories: string[]
}

export interface PatchSummary {
  name: string
  entryCount: number
}

export interface ViewedPatch {
  name: string
  entries: DraftEntry[]
}

// GET /api/recomp — absent (404) hors session AMEND/RECOMPOSITION.
export interface RecompState {
  isAmend: boolean
  rangeStart: number
  rangeEnd: number
  unresolvedConflicts: { filePath: string; optionPath: string }[]
}

export type Mode = 'NEW_PATCH' | 'AMEND' | 'RECOMPOSITION'

export function currentModeOf(recomp: RecompState | null): Mode {
  if (!recomp) return 'NEW_PATCH'
  return recomp.isAmend ? 'AMEND' : 'RECOMPOSITION'
}

export const entryKey = (filePath: string, optionPath: string) => `${filePath}::${optionPath}`
