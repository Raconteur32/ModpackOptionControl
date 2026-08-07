// Thin fetch wrappers, one group per route family (doc/gui-refacto-tech.md §6).

const jsonHeaders = { 'Content-Type': 'application/json' };

function postJson(url, body) {
    return fetch(url, { method: 'POST', headers: jsonHeaders, body: JSON.stringify(body ?? {}) });
}
function deleteJson(url, body) {
    return fetch(url, { method: 'DELETE', headers: jsonHeaders, body: JSON.stringify(body ?? {}) });
}

export const api = {
    diff: {
        files: (showAll = false) =>
            fetch(`/api/diff${showAll ? '?showAll=true' : ''}`).then(r => r.json()),
        file: (path) =>
            fetch(`/api/diff/${encodeURIComponent(path)}`).then(r => r.json()),
    },
    draft: {
        get: () => fetch('/api/draft').then(r => r.json()),
        add: (body) => postJson('/api/draft/entries', body).then(r => r.json().catch(() => ({}))),
        remove: (body) => deleteJson('/api/draft/entries', body).then(r => r.json().catch(() => ({}))),
        clear: () => fetch('/api/draft', { method: 'DELETE' }).then(r => r.json().catch(() => ({}))),
        finalize: (name) => postJson('/api/draft/finalize', { name }).then(async r => ({ ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) })),
        finalizeForAmend: () => fetch('/api/draft/finalize-for-amend', { method: 'POST' })
            .then(async r => ({ ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) })),
    },
    patches: {
        list: () => fetch('/api/patches').then(r => r.json()),
        get: (name) => fetch(`/api/patches/${encodeURIComponent(name)}`).then(r => r.json()),
        delete: (names) => deleteJson('/api/patches', { names }).then(async r => ({ ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) })),
    },
    recomp: {
        get: () => fetch('/api/recomp').then(r => r.status === 404 ? null : r.json()),
        start: (body) => postJson('/api/recomp', body).then(async r => ({ ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) })),
        cancel: () => fetch('/api/recomp', { method: 'DELETE' }),
        finalize: (name) => postJson('/api/recomp/finalize', { name }).then(async r => ({ ok: r.ok, status: r.status, body: await r.json().catch(() => ({})) })),
        diff: {
            files: () => fetch('/api/recomp/diff').then(r => r.status === 404 ? { files: [] } : r.json()),
            file: (path) => fetch(`/api/recomp/diff/${encodeURIComponent(path)}`).then(r => r.json()),
        },
        entries: {
            get: () => fetch('/api/recomp/entries').then(r => r.json()),
            add: (body) => postJson('/api/recomp/entries', body).then(r => r.json().catch(() => ({}))),
            remove: (body) => deleteJson('/api/recomp/entries', body).then(r => r.json().catch(() => ({}))),
        },
    },
    ignores: {
        get: () => fetch('/api/ignores').then(r => r.json()),
        add: (body) => postJson('/api/ignores', body).then(r => r.json().catch(() => ({}))),
        remove: (body) => deleteJson('/api/ignores', body).then(r => r.json().catch(() => ({}))),
        recomp: {
            get: () => fetch('/api/ignores/recomp').then(r => r.json()),
            add: (body) => postJson('/api/ignores/recomp', body).then(r => r.json().catch(() => ({}))),
            remove: (body) => deleteJson('/api/ignores/recomp', body).then(r => r.json().catch(() => ({}))),
        },
    },
};
