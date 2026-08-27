import { defineConfig } from 'vitest/config'

// Tests JS legacy uniquement — les tests du front React (frontend/) ont leur
// propre config Vite (gui_web/frontend/vite.config.ts).
export default defineConfig({
  test: {
    include: ['src/test/js/**/*.test.js'],
  },
})
