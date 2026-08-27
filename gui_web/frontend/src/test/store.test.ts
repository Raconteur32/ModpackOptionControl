// Tests du store (tasks 3.2/3.3/3.6) — port TS des tests reload.js legacy
// (refreshRecomp, branching par mode) + routing RESET (ex-actions.js) + purge
// des sélections par existence.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetStoreForTests, useStore } from '../store'
import { entryKey } from '../types'

interface Call { url: string; method: string; body: Record<string, unknown> | null }

const calls: Call[] = []

// Réponses par défaut du fake serveur ; surchargeable par test via `routes`.
let routes: Record<string, { status?: number; body?: unknown }> = {}

function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = String(input)
  const method = init?.method ?? 'GET'
  calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : null })
  const key = `${method} ${url}`
  const route = routes[key] ?? routes[url]
  const status = route?.status ?? 200
  const body = route?.body ?? (url === '/api/recomp' && method === 'GET' ? null : {})
  return Promise.resolve(
    new Response(JSON.stringify(body ?? {}), { status: body === null ? 404 : status }),
  )
}

const mutations = () => calls.filter((c) => c.method !== 'GET')

beforeEach(() => {
  resetStoreForTests()
  calls.length = 0
  routes = {
    '/api/recomp': { status: 404, body: null }, // pas de session par défaut
    '/api/diff': { body: { files: ['new-patch-file'] } },
    '/api/recomp/diff': { body: { files: ['recomp-file'] } },
    '/api/draft': { body: { entries: ['new-patch-entry'] } },
    '/api/recomp/entries': { body: { entries: ['recomp-entry'] } },
    '/api/patches': { body: { patches: [] } },
    '/api/ignores': { body: { entries: [], directories: [] } },
    '/api/ignores/recomp': { body: { entries: [] } },
  }
  vi.stubGlobal('fetch', vi.fn(fakeFetch))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const s = () => useStore.getState()

// ---------- Port de reload.test.js (5 tests) ----------

describe('reloadDiffFiles', () => {
  it('refreshes recomp by default before branching on mode', async () => {
    await s().reloadDiffFiles()
    expect(calls.some((c) => c.method === 'GET' && c.url === '/api/recomp')).toBe(true)
    expect(s().data.diffFiles).toEqual(['new-patch-file'])
  })

  it('skips the recomp refetch when refreshRecomp=false, reusing the stored recomp', async () => {
    useStore.setState((st) => ({ data: { ...st.data, recomp: { isAmend: true, rangeStart: 0, rangeEnd: 0, unresolvedConflicts: [] } } }))
    calls.length = 0
    await s().reloadDiffFiles(false)
    expect(calls.some((c) => c.url === '/api/recomp' && c.method === 'GET' && c.url === '/api/recomp')).toBe(false)
    expect(calls.some((c) => c.url === '/api/recomp/diff')).toBe(true)
    expect(s().data.diffFiles).toEqual(['recomp-file'])
  })

  it('fetches the recomp-scoped endpoint once recomp reflects an active session', async () => {
    routes['/api/recomp'] = { body: { isAmend: true, rangeStart: 0, rangeEnd: 0, unresolvedConflicts: [] } }
    await s().reloadDiffFiles()
    expect(calls.some((c) => c.url === '/api/recomp/diff')).toBe(true)
    expect(calls.some((c) => c.url === '/api/diff')).toBe(false)
  })
})

describe('reloadDraft', () => {
  it('refreshes recomp by default before branching on mode', async () => {
    await s().reloadDraft()
    expect(calls.some((c) => c.method === 'GET' && c.url === '/api/recomp')).toBe(true)
    expect(s().data.draftEntries).toEqual(['new-patch-entry'])
  })

  it('skips the recomp refetch when refreshRecomp=false, reusing the stored recomp', async () => {
    useStore.setState((st) => ({ data: { ...st.data, recomp: { isAmend: true, rangeStart: 0, rangeEnd: 0, unresolvedConflicts: [] } } }))
    calls.length = 0
    await s().reloadDraft(false)
    expect(calls.filter((c) => c.url === '/api/recomp')).toEqual([])
    expect(calls.some((c) => c.url === '/api/recomp/entries')).toBe(true)
    expect(s().data.draftEntries).toEqual(['recomp-entry'])
  })
})

// ---------- Port du routing requestReset/requestBulkReset (6 tests) ----------

const FILE = 'config/app.json'
const OPT = "$['a']"

function setData(partial: Partial<ReturnType<typeof s>['data']>) {
  useStore.setState((st) => ({ data: { ...st.data, ...partial } }))
}

describe('resetEntry routing', () => {
  it('unstages a staged entry via DELETE /api/draft/entries (NEW_PATCH)', async () => {
    setData({ draftEntries: [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT', kind: 'VALUE' }] })
    await s().resetEntry(FILE, OPT)
    expect(mutations()).toEqual([{ url: '/api/draft/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }])
  })

  it('unignores a session ignore via DELETE /api/ignores with its kind', async () => {
    setData({ ignores: { entries: [{ filePath: FILE, optionPath: OPT, kind: 'SESSION', targetValue: null }], directories: [] } })
    await s().resetEntry(FILE, OPT)
    expect(mutations()).toEqual([{ url: '/api/ignores', method: 'DELETE', body: { filePath: FILE, optionPath: OPT, kind: 'SESSION' } }])
  })

  it('unignores a recomp ignore via DELETE /api/ignores/recomp', async () => {
    setData({
      recomp: { isAmend: false, rangeStart: 0, rangeEnd: 1, unresolvedConflicts: [] },
      recompIgnores: [{ filePath: FILE, optionPath: OPT }],
    })
    await s().resetEntry(FILE, OPT)
    expect(mutations()).toEqual([{ url: '/api/ignores/recomp', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }])
  })

  it('unstages via /api/recomp/entries in AMEND/RECOMP mode', async () => {
    setData({
      recomp: { isAmend: true, rangeStart: 0, rangeEnd: 0, unresolvedConflicts: [] },
      draftEntries: [{ filePath: FILE, optionPath: OPT, mode: 'OVERRIDE', kind: 'VALUE' }],
    })
    await s().resetEntry(FILE, OPT)
    expect(mutations()).toEqual([{ url: '/api/recomp/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } }])
  })

  it('does nothing for an UNSTAGED row', async () => {
    await s().resetEntry(FILE, OPT)
    expect(mutations()).toEqual([])
  })
})

describe('bulkResetEntries', () => {
  it('applies the per-row inverse across a mixed selection, skipping UNSTAGED', async () => {
    setData({
      draftEntries: [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT', kind: 'VALUE' }],
      ignores: { entries: [{ filePath: FILE, optionPath: "$['b']", kind: 'PERMANENT', targetValue: null }], directories: [] },
    })
    await s().bulkResetEntries([
      { filePath: FILE, optionPath: OPT },        // staged → unstage
      { filePath: FILE, optionPath: "$['b']" },   // ignored → unignore
      { filePath: FILE, optionPath: "$['c']" },   // unstaged → skip
    ])
    expect(mutations()).toEqual([
      { url: '/api/draft/entries', method: 'DELETE', body: { filePath: FILE, optionPath: OPT } },
      { url: '/api/ignores', method: 'DELETE', body: { filePath: FILE, optionPath: "$['b']", kind: 'PERMANENT' } },
    ])
  })
})

// ---------- Purge par existence (task 3.3) ----------

describe('selection purge by existence', () => {
  it('reloadDraft drops staging selections whose entry disappeared', async () => {
    setData({ draftEntries: [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT', kind: 'VALUE' }] })
    useStore.setState((st) => ({
      ui: { ...st.ui, selectedStaging: new Set([entryKey(FILE, OPT), entryKey(FILE, "$['gone']")]) },
    }))
    routes['/api/draft'] = { body: { entries: [{ filePath: FILE, optionPath: OPT, mode: 'DEFAULT', kind: 'VALUE' }] } }
    await s().reloadDraft()
    expect([...s().ui.selectedStaging]).toEqual([entryKey(FILE, OPT)])
  })

  it('reloadPatches drops selected patch names that disappeared and a deleted viewedPatch', async () => {
    setData({
      patches: [{ name: 'a', entryCount: 1 }],
      viewedPatch: { name: 'gone', entries: [] },
    })
    useStore.setState((st) => ({ ui: { ...st.ui, selectedPatches: new Set(['a', 'gone']) } }))
    routes['/api/patches'] = { body: { patches: [{ name: 'a', entryCount: 1 }] } }
    await s().reloadPatches()
    expect([...s().ui.selectedPatches]).toEqual(['a'])
    expect(s().data.viewedPatch).toBeNull()
  })

  it('reloadDiffFiles drops file selections and node keys whose file disappeared', async () => {
    useStore.setState((st) => ({
      ui: {
        ...st.ui,
        selectedFiles: new Set([FILE, 'config/gone.json']),
        selectedNodes: new Set([entryKey(FILE, OPT), entryKey('config/gone.json', '$')]),
      },
    }))
    routes['/api/diff'] = { body: { files: [{ path: FILE, kind: 'CHANGED', stagedCount: 0, hasUnresolved: false, ignored: false }] } }
    await s().reloadDiffFiles()
    expect([...s().ui.selectedFiles]).toEqual([FILE])
    expect([...s().ui.selectedNodes]).toEqual([entryKey(FILE, OPT)])
  })

  it('loadDiffFile drops node selections/expansions/raw flags for vanished option paths', async () => {
    routes['/api/diff/config%2Fapp.json'] = {
      body: {
        tree: [{ path: '$', label: '$', kind: 'CHANGED', hasChildren: true, children: [
          { path: OPT, label: 'a', kind: 'CHANGED', oldValue: 1, newValue: 2, hasChildren: false, children: [] },
        ] }],
      },
    }
    useStore.setState((st) => ({
      ui: {
        ...st.ui,
        selectedNodes: new Set([entryKey(FILE, OPT), entryKey(FILE, "$['gone']")]),
        expandedNodes: new Set([entryKey(FILE, '$'), entryKey(FILE, "$['gone']")]),
        rawNodes: new Set([entryKey(FILE, "$['gone']")]),
      },
    }))
    await s().loadDiffFile(FILE)
    expect([...s().ui.selectedNodes]).toEqual([entryKey(FILE, OPT)])
    expect([...s().ui.expandedNodes]).toEqual([entryKey(FILE, '$')])
    expect([...s().ui.rawNodes]).toEqual([])
  })
})
