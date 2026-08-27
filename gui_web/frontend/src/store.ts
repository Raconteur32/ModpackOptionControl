// Store central (Zustand) — design D4. Deux slices : `data` (miroir de
// state.js legacy) et `ui` (miroir de uiState + breadcrumbPath + focusRequest).
// Les actions async portent l'orchestration d'app.js (chargements, purge des
// sélections par existence) ; sync.ts possède la table WS -> actions.
//
// Coexistence (design D3) : le legacy accède au store via l'adaptateur
// state.js (proxys sur window.__moc exposé par mount.tsx).
import { create } from 'zustand'
import { api } from './api'
import { resolveRowState } from './domain/rowState'
import type {
  DiffNode,
  DraftEntry,
  FileSummary,
  Ignores,
  PatchSummary,
  RecompState,
  ViewedPatch,
} from './types'
import { currentModeOf, entryKey } from './types'

export interface DataSlice {
  recomp: RecompState | null
  diffFiles: FileSummary[]
  currentFile: string | null
  currentTree: DiffNode[]
  draftEntries: DraftEntry[]
  patches: PatchSummary[]
  viewedPatch: ViewedPatch | null
  ignores: Ignores
  recompIgnores: { filePath: string; optionPath: string }[]
}

export type FocusedComponent = 'main' | 'staging' | 'history' | 'filetree' | null

export interface FocusRequest {
  filePath: string
  optionPath: string
  nonce: number
}

export interface UiSlice {
  expandedDirs: Set<string>
  expandedNodes: Set<string>
  selectedNodes: Set<string>
  selectionAnchor: string | null
  selectedStaging: Set<string>
  stagingAnchor: string | null
  selectedPatches: Set<string>
  patchAnchor: string | null
  selectedFiles: Set<string>
  fileAnchor: string | null
  focusedComponent: FocusedComponent
  rawNodes: Set<string>
  openDropdown: { id: string; up: boolean } | null
  displayMode: 'GREYED' | 'FILTERED'
  ignoresPopoverOpen: boolean
  recompIgnoresPopoverOpen: boolean
  ignoresFilterKind: 'SESSION' | 'VALUE' | 'PERMANENT' | 'DIRECTORY' | null
  ignoresSearch: string
  // Déclaré proprement (était un champ implicite de uiState legacy, task 3.3).
  breadcrumbPath: string | null
  // Navigation cross-frontière legacy <-> React (design D3/D5) : un panneau
  // demande le focus d'une option, le consommateur (legacy app.js pour
  // l'instant) charge le fichier et scrolle vers la ligne.
  focusRequest: FocusRequest | null
}

function initialData(): DataSlice {
  return {
    recomp: null,
    diffFiles: [],
    currentFile: null,
    currentTree: [],
    draftEntries: [],
    patches: [],
    viewedPatch: null,
    ignores: { entries: [], directories: [] },
    recompIgnores: [],
  }
}

function initialUi(): UiSlice {
  return {
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
  }
}

// ---------- Purge par existence (task 3.3) ----------
// Une sélection ne survit que si l'item sous-jacent existe encore (pas de
// purge par visibilité : un nœud collapsé/filtré garde sa sélection).

function collectNodePaths(nodes: DiffNode[], into: Set<string>): Set<string> {
  for (const n of nodes) {
    into.add(n.path)
    if (n.children) collectNodePaths(n.children, into)
  }
  return into
}

// Filtre les clés "filePath::optionPath" d'un Set selon un prédicat portant
// sur (filePath, optionPath). Retourne un nouveau Set (immutabilité zustand).
function filterEntryKeys(set: Set<string>, keep: (filePath: string, optionPath: string) => boolean): Set<string> {
  const next = new Set<string>()
  for (const k of set) {
    const sep = k.indexOf('::')
    if (sep !== -1 && keep(k.slice(0, sep), k.slice(sep + 2))) next.add(k)
  }
  return next
}

export interface StoreActions {
  loadDiffFile: (path: string | null) => Promise<void>
  reloadDiffFiles: (refreshRecomp?: boolean) => Promise<void>
  reloadDraft: (refreshRecomp?: boolean) => Promise<void>
  reloadPatches: () => Promise<void>
  reloadIgnores: () => Promise<void>
  reloadRecompIgnores: () => Promise<void>
  reloadRecomp: () => Promise<void>
  reloadDiff: () => Promise<void>
  loadAll: () => Promise<void>
  resetMainAreaFocus: () => Promise<void>
  selectFile: (path: string | null) => Promise<void>
  requestFocus: (filePath: string, optionPath: string) => void
  // Reload composite après une action staging/ignore (ex-setActionsReload).
  reloadAfterAction: () => Promise<void>
  // RESET (design D2 de dropdown-reset-action) : inverse unifié — retire
  // l'entrée stagée ou la règle d'ignore, sans confirmation.
  resetEntry: (filePath: string, optionPath: string) => Promise<void>
  bulkResetEntries: (targets: { filePath: string; optionPath: string }[]) => Promise<void>
  // --- Panneau staging React (task 4.1) ---
  // Stage best-effort (design D5) : les adds sont tentés un à un, un échec
  // n'interrompt pas le lot ; un seul reload composite à la fin.
  stageMany: (entries: { filePath: string; optionPath: string; mode: 'DEFAULT' | 'OVERRIDE' }[]) => Promise<void>
  // Changement de mode d'entries déjà stagées (menu par ligne / bulk bar) :
  // un add avec le nouveau mode remplace l'entry existante côté serveur.
  setDraftEntriesMode: (
    targets: { filePath: string; optionPath: string }[],
    mode: 'DEFAULT' | 'OVERRIDE',
  ) => Promise<void>
  // Retrait direct du staging (bouton ×, bulk Remove, RESET staging) — sans
  // confirmation, parité legacy removeDraftEntry{,s}Direct.
  removeDraftEntries: (targets: { filePath: string; optionPath: string }[]) => Promise<void>
  // --- Sessions (boutons d'action du staging, flow §12a/b/c) ---
  createPatch: (name: string) => Promise<void>
  startAmendFromStaging: () => Promise<void>
  finalizeSession: (name: string) => Promise<void>
  cancelSession: () => Promise<void>
  setUi: <K extends keyof UiSlice>(key: K, value: UiSlice[K]) => void
}

export type Store = { data: DataSlice; ui: UiSlice } & StoreActions

// Endpoints de backing selon le mode courant (NEW_PATCH vs AMEND/RECOMP).
function backing(data: DataSlice) {
  return currentModeOf(data.recomp) === 'NEW_PATCH'
    ? { add: api.draft.add, remove: api.draft.remove }
    : { add: api.recomp.entries.add, remove: api.recomp.entries.remove }
}

export const useStore = create<Store>()((set, get) => {
  const setData = (partial: Partial<DataSlice>) => set((s) => ({ data: { ...s.data, ...partial } }))
  const setUiPartial = (partial: Partial<UiSlice>) => set((s) => ({ ui: { ...s.ui, ...partial } }))

  return {
    data: initialData(),
    ui: initialUi(),

    async loadDiffFile(path) {
      if (path === null) {
        setData({ currentFile: null, currentTree: [] })
        return
      }
      const mode = currentModeOf(get().data.recomp)
      const res = mode === 'NEW_PATCH' ? await api.diff.file(path) : await api.recomp.diff.file(path)
      const tree = res.tree ?? []
      setData({ currentFile: path, currentTree: tree })
      // Purge par existence : les clés du fichier courant qui ne pointent plus
      // vers un nœud existant (le diff a changé sous nos pieds).
      const existing = collectNodePaths(tree, new Set())
      const { ui } = get()
      setUiPartial({
        selectedNodes: filterEntryKeys(ui.selectedNodes, (f, o) => f !== path || existing.has(o)),
        expandedNodes: filterEntryKeys(ui.expandedNodes, (f, o) => f !== path || existing.has(o)),
        rawNodes: filterEntryKeys(ui.rawNodes, (f, o) => f !== path || existing.has(o)),
      })
    },

    // refreshRecomp=true rafraîchit data.recomp avant de brancher sur le mode :
    // plusieurs events WS (diff_changed, patches_changed, recomp_changed) sont
    // broadcast ensemble à la finalisation/annulation d'une session et leurs
    // handlers tournent en concurrence — réutiliser un data.recomp caché
    // courserait le handler qui le met à null. loadAll passe false car il
    // vient déjà de le rafraîchir lui-même.
    async reloadDiffFiles(refreshRecomp = true) {
      if (refreshRecomp) setData({ recomp: await api.recomp.get() })
      const mode = currentModeOf(get().data.recomp)
      const files = mode === 'NEW_PATCH'
        ? (await api.diff.files()).files
        : (await api.recomp.diff.files()).files
      setData({ diffFiles: files })
      // Purge : fichiers disparus du diff.
      const existingPaths = new Set(files.map((f) => f.path))
      const { ui } = get()
      const keepFile = (f: string) => existingPaths.has(f)
      setUiPartial({
        selectedFiles: new Set([...ui.selectedFiles].filter((p) => existingPaths.has(p))),
        selectedNodes: filterEntryKeys(ui.selectedNodes, (f) => keepFile(f)),
        expandedNodes: filterEntryKeys(ui.expandedNodes, (f) => keepFile(f)),
        rawNodes: filterEntryKeys(ui.rawNodes, (f) => keepFile(f)),
      })
    },

    async reloadDraft(refreshRecomp = true) {
      if (refreshRecomp) setData({ recomp: await api.recomp.get() })
      const mode = currentModeOf(get().data.recomp)
      const entries = mode === 'NEW_PATCH'
        ? (await api.draft.get()).entries
        : (await api.recomp.entries.get()).entries
      setData({ draftEntries: entries })
      // Purge : entrées de staging disparues.
      const existing = new Set(entries.map((e) => entryKey(e.filePath, e.optionPath)))
      const { ui } = get()
      setUiPartial({
        selectedStaging: new Set([...ui.selectedStaging].filter((k) => existing.has(k))),
      })
    },

    async reloadPatches() {
      const patches = (await api.patches.list()).patches
      const { data, ui } = get()
      // Le patch ouvert en [View] read-only a pu être supprimé (par un autre
      // client) — on le lâche plutôt que d'afficher du contenu périmé.
      const viewedPatch = data.viewedPatch && !patches.some((p) => p.name === data.viewedPatch!.name)
        ? null
        : data.viewedPatch
      setData({ patches, viewedPatch })
      // Purge : patches disparus.
      const names = new Set(patches.map((p) => p.name))
      setUiPartial({
        selectedPatches: new Set([...ui.selectedPatches].filter((n) => names.has(n))),
      })
    },

    async reloadIgnores() {
      setData({ ignores: await api.ignores.get() })
    },

    // Les ignores de recomposition sont scopés à une session active ; en dehors
    // il n'y a rien à fetcher.
    async reloadRecompIgnores() {
      if (!get().data.recomp) {
        setData({ recompIgnores: [] })
        return
      }
      setData({ recompIgnores: (await api.ignores.recomp.get()).entries ?? [] })
    },

    async reloadRecomp() {
      setData({ recomp: await api.recomp.get() })
    },

    async reloadDiff() {
      await get().reloadDiffFiles()
      const { currentFile } = get().data
      if (currentFile) await get().loadDiffFile(currentFile)
    },

    async loadAll() {
      setData({ recomp: await api.recomp.get() })
      await get().reloadDiffFiles(false)
      await get().reloadDraft(false)
      setData({
        patches: (await api.patches.list()).patches,
        ignores: await api.ignores.get(),
      })
      await get().reloadRecompIgnores()
    },

    // Lâche tout focus fichier/patch devenu invalide après une transition de
    // mode, puis re-sélectionne le fichier ouvert (s'il existe encore) contre
    // la source de diff du nouveau mode.
    async resetMainAreaFocus() {
      const path = get().data.currentFile
      setData({ viewedPatch: null, currentFile: null, currentTree: [] })
      setUiPartial({ breadcrumbPath: null })
      if (path && get().data.diffFiles.some((f) => f.path === path)) {
        await get().loadDiffFile(path)
      }
    },

    async selectFile(path) {
      // Quitte la vue [View] read-only (le cas échéant) pour naviguer le diff.
      setData({ viewedPatch: null })
      setUiPartial({ breadcrumbPath: null })
      await get().loadDiffFile(path)
    },

    requestFocus(filePath, optionPath) {
      setUiPartial({ focusRequest: { filePath, optionPath, nonce: Date.now() } })
    },

    async reloadAfterAction() {
      await get().reloadDiff()
      await get().reloadDraft()
      await get().reloadIgnores()
      if (get().data.recomp) {
        await get().reloadRecomp()
        await get().reloadRecompIgnores()
      }
    },

    async resetEntry(filePath, optionPath) {
      await applyReset(get().data, backing(get().data), [{ filePath, optionPath }])
      await get().reloadAfterAction()
    },

    async bulkResetEntries(targets) {
      await applyReset(get().data, backing(get().data), targets)
      await get().reloadAfterAction()
    },

    async stageMany(entries) {
      const b = backing(get().data)
      for (const e of entries) await b.add(e)
      await get().reloadAfterAction()
    },

    async setDraftEntriesMode(targets, mode) {
      await get().stageMany(targets.map((t) => ({ ...t, mode })))
    },

    async removeDraftEntries(targets) {
      const b = backing(get().data)
      for (const t of targets) await b.remove(t)
      await get().reloadAfterAction()
    },

    async createPatch(name) {
      const res = await api.draft.finalize(name)
      if (!res.ok) {
        alert(`Could not create patch: ${res.body.error ?? 'unknown error'}`)
        return
      }
      await get().reloadAfterAction()
    },

    // Amend entry point 1 (flow §12b) : fold du DraftPatch courant dans
    // l'amend dir puis démarrage de la session sur le dernier patch.
    async startAmendFromStaging() {
      const { draftEntries, patches } = get().data
      if (draftEntries.length === 0 || patches.length === 0) return
      const finalizeRes = await api.draft.finalizeForAmend()
      if (!finalizeRes.ok) {
        alert(`Could not start amend: ${finalizeRes.body.error ?? 'unknown error'}`)
        return
      }
      const lastPatchIndex = finalizeRes.body.lastPatchIndex as number
      const startRes = await api.recomp.start({
        startIdx: lastPatchIndex,
        endIdx: lastPatchIndex,
        isAmend: true,
      })
      if (!startRes.ok) {
        alert(`Could not start amend: ${startRes.body.error ?? 'unknown error'}`)
        return
      }
      await get().reloadAfterAction()
    },

    async finalizeSession(name) {
      const res = await api.recomp.finalize(name)
      if (!res.ok) {
        alert(`Could not finalize: ${res.body.error ?? 'unknown error'}`)
        return
      }
      await get().reloadAfterAction()
    },

    // flow §12b/§12c : Cancel d'une session discard TOUT — RecompositionDraft
    // (amend dir inclus) ET DraftPatch — retour à un NEW_PATCH propre.
    async cancelSession() {
      await api.recomp.cancel()
      await api.draft.clear()
      await get().reloadAfterAction()
    },

    setUi(key, value) {
      setUiPartial({ [key]: value })
    },
  }
})

// Inverse par ligne sur toute la sélection : unstage les lignes stagées,
// un-ignore les lignes ignorées, skip les UNSTAGED. Partagé par resetEntry et
// bulkResetEntries (l'un est le cas singleton de l'autre).
async function applyReset(
  data: DataSlice,
  b: { remove: (body: { filePath: string; optionPath: string }) => Promise<unknown> },
  targets: { filePath: string; optionPath: string }[],
): Promise<void> {
  const rowData = { draftEntries: data.draftEntries, ignores: data.ignores.entries, recompIgnores: data.recompIgnores }
  for (const { filePath, optionPath } of targets) {
    const { state: rowState, ignoreKind } = resolveRowState(rowData, filePath, optionPath)
    if (rowState === 'IGNORED') {
      if (ignoreKind === 'RECOMP') await api.ignores.recomp.remove({ filePath, optionPath })
      else if (ignoreKind && ignoreKind !== 'DIRECTORY') await api.ignores.remove({ filePath, optionPath, kind: ignoreKind })
    } else if (rowState !== 'UNSTAGED') {
      await b.remove({ filePath, optionPath })
    }
  }
}

// Reset complet — tests uniquement (le store est un singleton module).
export function resetStoreForTests(): void {
  useStore.setState({ data: initialData(), ui: initialUi() })
}

// ---------- Sélecteurs dérivés (miroir des helpers de state.js) ----------

export function currentMode(data: DataSlice) {
  return currentModeOf(data.recomp)
}

// Nom du patch amendé (AMEND cible une range single-patch), ou null.
export function amendTargetName(data: DataSlice): string | null {
  if (currentModeOf(data.recomp) !== 'AMEND' || !data.recomp) return null
  return data.patches[data.recomp.rangeStart]?.name ?? null
}

// Noms { start, end } de la range de la session RECOMPOSITION active.
export function recompRangeNames(data: DataSlice): { start: string; end: string } | null {
  if (currentModeOf(data.recomp) !== 'RECOMPOSITION' || !data.recomp) return null
  const start = data.patches[data.recomp.rangeStart]?.name
  const end = data.patches[data.recomp.rangeEnd]?.name
  if (!start || !end) return null
  return { start, end }
}
