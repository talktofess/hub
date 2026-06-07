import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// @ts-ignore - plain Node ESM module (typed via server/localApi.d.ts)
import { handleLocalApi } from './server/localApi.js';

// Serves the local media + config API during `vite dev` and `vite preview`,
// so uploads/config work while editing exactly as they do for OBS.
function localApi() {
  const mw = (server: any) => server.middlewares.use(async (req: any, res: any, next: any) => {
    try { if (await handleLocalApi(req, res)) return; } catch (e) { res.statusCode = 500; res.end(String(e)); return; }
    next();
  });
  return { name: 'local-api', configureServer: mw, configurePreviewServer: mw };
}

export default defineConfig({
  plugins: [react(), localApi()],
  base: './',
  build: { outDir: 'dist', sourcemap: false },
  server: { host: true, port: 3000 },
  preview: { port: 3000 },
});
