// Page de dev (vite dev uniquement) — pas l'app embarquée : celle-ci monte
// ses racines via mount.tsx dans la page legacy. Cette page sert au
// développement isolé des panneaux migrés avec HMR, adossée au même store +
// synchro WS (proxy /api,/ws vers le serveur moc-web, cf. vite.config.ts).
import { Box } from '@chakra-ui/react'
import { initSync } from './sync'
import { StagingPanel } from './panels/StagingPanel'

initSync()

export function App() {
  return (
    <Box h="100vh" display="flex" flexDirection="column">
      <StagingPanel />
    </Box>
  )
}
