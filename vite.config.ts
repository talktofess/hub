import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Relative base so the built app works at the site root on Vercel and also when
// an OBS Browser Source loads it from any path.
export default defineConfig({
  plugins: [react()],
  base: './',
  build: { outDir: 'dist', sourcemap: false },
  server: { host: true, port: 3000 },
  preview: { port: 3000 },
});
