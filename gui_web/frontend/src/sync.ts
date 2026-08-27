// Table WS -> actions du store (design D4), portée verbatim d'app.js legacy.
// Le store est la seule source de vérité ; les panneaux legacy re-rendent via
// leur subscription (app.js), les panneaux React via useStore.
import { useStore } from './store'
import { connectWs, onWsEvent } from './ws'

export function initSync(): void {
  const s = () => useStore.getState()

  onWsEvent(async (type) => {
    switch (type) {
      case 'diff_changed':
        await s().reloadDiff()
        break
      case 'draft_changed':
        await s().reloadDraft()
        break
      case 'patches_changed':
        await s().reloadPatches()
        break
      case 'recomp_changed':
        await s().loadAll()
        break
      case 'ignores_changed':
        await s().reloadIgnores()
        await s().reloadRecompIgnores()
        await s().reloadDiff()
        break
      case 'conflicts_changed':
        await s().reloadRecomp()
        break
    }
  })

  connectWs()
  void s().loadAll()
}
