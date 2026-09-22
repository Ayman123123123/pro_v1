import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// Hot-reload talks to the real Compose stack on 8088. Vite itself stays on
// 5173 so it never steals Nginx's port or the old Node mock on 8080.
const apiTarget = process.env.RED_API_TARGET || 'http://127.0.0.1:8088';
const wsTarget = apiTarget.replace(/^http/, 'ws');

const proxy = {
  '/api': { target: apiTarget, changeOrigin: true, timeout: 30000 },
  '/health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
  '/sfu-health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
  '/ws': { target: wsTarget, ws: true, changeOrigin: true, timeout: 10000 },
};

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          antd: ['antd', '@ant-design/icons'],
          charts: ['echarts', 'echarts-for-react']
        }
      }
    }
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    allowedHosts: true,
    cors: true,
    proxy,
  },
  preview: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: false,
    allowedHosts: true,
    cors: true,
    proxy,
  }
});
