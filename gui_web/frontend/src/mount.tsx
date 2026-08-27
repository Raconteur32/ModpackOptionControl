// Point d'entrée du bundle React embarqué dans la page legacy (coexistence,
// design D3). Responsabilités, dans l'ordre (ce module est chargé AVANT
// js/app.js legacy, cf. index.html) :
//   1. exposer le store sur window.__moc pour l'adaptateur state.js legacy ;
//   2. démarrer la synchro WS + le chargement initial (sync.ts) ;
//   3. monter les racines React des panneaux migrés (conteneurs
//      `<div data-react-panel="...">` posés dans index.html, groupe 4).
import type { ComponentType } from 'react'
import { createRoot } from 'react-dom/client'
import { ChakraProvider, defaultSystem } from '@chakra-ui/react'
import { useStore } from './store'
import { initSync } from './sync'
import { StagingPanel } from './panels/StagingPanel'

declare global {
  interface Window {
    __moc?: typeof useStore
  }
}

window.__moc = useStore
initSync()

// Registre des panneaux migrés (un par `<div data-react-panel>` d'index.html).
const panels: Record<string, ComponentType> = {
  staging: StagingPanel,
}

for (const el of document.querySelectorAll<HTMLElement>('[data-react-panel]')) {
  const Panel = panels[el.dataset.reactPanel ?? '']
  if (!Panel) continue
  createRoot(el).render(
    <ChakraProvider value={defaultSystem}>
      <Panel />
    </ChakraProvider>,
  )
}
