/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev proxy: the moc-web server under test (see AGENTS.md — fake instance on 7599).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:7599',
      '/ws': { target: 'ws://localhost:7599', ws: true },
    },
  },
  build: {
    // Bundle embarqué dans la page legacy (coexistence) : pas d'index.html,
    // noms fixes référencés par static/index.html. Vidé à chaque build — le
    // reste de static/ (app legacy) n'est pas touché. Voir groupe 5 pour le
    // basculement complet vers une app standalone.
    outDir: '../src/main/resources/static/assets',
    emptyOutDir: true,
    rollupOptions: {
      input: 'src/mount.tsx',
      output: {
        entryFileNames: '[name].js',
        assetFileNames: '[name][extname]',
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    globals: true,
  },
})
