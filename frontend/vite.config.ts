import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies /api to the Spring Boot backend, so the app is same-origin in dev and prod.
export default defineConfig({
  // Sub-path for GitHub Pages builds (e.g. /math-mission/); '/' for the server and the Android app.
  base: process.env.VITE_BASE ?? '/',
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8080', '/actuator': 'http://localhost:8080' },
  },
  build: { outDir: 'dist', sourcemap: true },
});
