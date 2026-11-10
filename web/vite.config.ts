import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    // 直接产出到 Spring Boot 的静态资源目录，前后端同目录、一条命令启动
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    chunkSizeWarningLimit: 900
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8085', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8085', ws: true }
    }
  }
});
