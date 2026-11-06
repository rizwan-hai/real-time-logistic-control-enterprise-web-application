import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
const sourcePath = decodeURIComponent(new URL('./src', import.meta.url).pathname).replace(/^\/([A-Za-z]:\/)/, '$1');
export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': sourcePath } },
  server: { port: 5173, proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], css: true },
});
