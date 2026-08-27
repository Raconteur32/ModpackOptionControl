// Port TS de api.js — thin fetch wrappers, une famille par groupe de routes
// (doc/gui-refacto-tech.md §6). Aucun changement de contrat.
import type {
  DiffNode,
  DraftEntry,
  FileSummary,
  Ignores,
  PatchSummary,
  RecompState,
} from './types'

const jsonHeaders = { 'Content-Type': 'application/json' }

function postJson(url: string, body?: unknown) {
  return fetch(url, { method: 'POST', headers: jsonHeaders, body: JSON.stringify(body ?? {}) })
}
function deleteJson(url: string, body?: unknown) {
  return fetch(url, { method: 'DELETE', headers: jsonHeaders, body: JSON.stringify(body ?? {}) })
}
// Réponses dont le corps peut être vide ou non-JSON (les routes draft/recomp
// renvoient parfois juste un status) — on avale l'erreur de parsing.
async function jsonOrEmpty(r: Response): Promise<Record<string, unknown>> {
  return r.json().catch(() => ({}))
}
async function statusResult(r: Response): Promise<{ ok: boolean; status: number; body: Record<string, unknown> }> {
  return { ok: r.ok, status: r.status, body: await jsonOrEmpty(r) }
}

export interface DraftAddBody { filePath: string; optionPath: string; mode: 'DEFAULT' | 'OVERRIDE' }
export interface DraftRemoveBody { filePath: string; optionPath: string }
export interface IgnoreAddBody { filePath: string; optionPath: string; kind: string; targetValue?: string | null }
export interface IgnoreRemoveBody { filePath: string; optionPath: string; kind: string }
export interface RecompEntryAddBody { filePath: string; optionPath: string; mode?: 'DEFAULT' | 'OVERRIDE'; action?: 'ignore'; kind?: string }
export interface RecompStartBody { startIdx: number; endIdx: number; isAmend: boolean }

export const api = {
  diff: {
    files: (showAll = false) =>
      fetch(`/api/diff${showAll ? '?showAll=true' : ''}`).then((r) => r.json()) as Promise<{ files: FileSummary[] }>,
    file: (path: string) =>
      fetch(`/api/diff/${encodeURIComponent(path)}`).then((r) => r.json()) as Promise<{ file?: FileSummary; tree?: DiffNode[] }>,
  },
  draft: {
    get: () => fetch('/api/draft').then((r) => r.json()) as Promise<{ entries: DraftEntry[] }>,
    add: (body: DraftAddBody) => postJson('/api/draft/entries', body).then(jsonOrEmpty),
    remove: (body: DraftRemoveBody) => deleteJson('/api/draft/entries', body).then(jsonOrEmpty),
    clear: () => fetch('/api/draft', { method: 'DELETE' }).then(jsonOrEmpty),
    finalize: (name: string) => postJson('/api/draft/finalize', { name }).then(statusResult),
    finalizeForAmend: () => fetch('/api/draft/finalize-for-amend', { method: 'POST' }).then(statusResult),
  },
  patches: {
    list: () => fetch('/api/patches').then((r) => r.json()) as Promise<{ patches: PatchSummary[] }>,
    get: (name: string) =>
      fetch(`/api/patches/${encodeURIComponent(name)}`).then((r) => r.json()) as Promise<{ name: string; entries?: DraftEntry[] }>,
    delete: (names: string[]) => deleteJson('/api/patches', { names }).then(statusResult),
  },
  recomp: {
    get: () =>
      fetch('/api/recomp').then((r) => (r.status === 404 ? null : r.json())) as Promise<RecompState | null>,
    start: (body: RecompStartBody) => postJson('/api/recomp', body).then(statusResult),
    cancel: () => fetch('/api/recomp', { method: 'DELETE' }),
    finalize: (name: string) => postJson('/api/recomp/finalize', { name }).then(statusResult),
    diff: {
      files: () =>
        fetch('/api/recomp/diff').then((r) => (r.status === 404 ? { files: [] } : r.json())) as Promise<{ files: FileSummary[] }>,
      file: (path: string) =>
        fetch(`/api/recomp/diff/${encodeURIComponent(path)}`).then((r) => r.json()) as Promise<{ file?: FileSummary; tree?: DiffNode[] }>,
    },
    entries: {
      get: () => fetch('/api/recomp/entries').then((r) => r.json()) as Promise<{ entries: DraftEntry[] }>,
      add: (body: RecompEntryAddBody) => postJson('/api/recomp/entries', body).then(jsonOrEmpty),
      remove: (body: DraftRemoveBody) => deleteJson('/api/recomp/entries', body).then(jsonOrEmpty),
    },
  },
  ignores: {
    get: () => fetch('/api/ignores').then((r) => r.json()) as Promise<Ignores>,
    add: (body: IgnoreAddBody) => postJson('/api/ignores', body).then(jsonOrEmpty),
    remove: (body: IgnoreRemoveBody) => deleteJson('/api/ignores', body).then(jsonOrEmpty),
    recomp: {
      get: () => fetch('/api/ignores/recomp').then((r) => r.json()) as Promise<{ entries?: { filePath: string; optionPath: string }[] }>,
      add: (body: { filePath: string; optionPath: string }) => postJson('/api/ignores/recomp', body).then(jsonOrEmpty),
      remove: (body: { filePath: string; optionPath: string }) => deleteJson('/api/ignores/recomp', body).then(jsonOrEmpty),
    },
  },
}
