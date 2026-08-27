// Port TS de ws.js — WebSocket unidirectionnel server -> client, auto-reconnect.
// Contrairement au legacy (un handler par type), on expose une subscription :
// sync.ts possède la table type -> actions du store (design D4).

export type WsListener = (type: string) => void

const listeners = new Set<WsListener>()

export function onWsEvent(fn: WsListener): () => void {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export function connectWs(): void {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  const ws = new WebSocket(`${proto}://${location.host}/ws`)
  ws.onmessage = ({ data }) => {
    try {
      const { type } = JSON.parse(data as string) as { type: string }
      for (const fn of listeners) fn(type)
    } catch {
      // message non-JSON : ignoré (le serveur n'émet que du JSON typé)
    }
  }
  ws.onclose = () => setTimeout(connectWs, 2000)
  ws.onerror = () => ws.close()
}
