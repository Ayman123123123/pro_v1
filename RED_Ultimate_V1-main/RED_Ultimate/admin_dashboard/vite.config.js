import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiTarget = process.env.RED_API_TARGET || 'http://127.0.0.1:8080';
const wsTarget = apiTarget.replace(/^http/, 'ws');

export default defineConfig({
  plugins: [react()],
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
    port: 8088,
    strictPort: false,
    allowedHosts: true,
    cors: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/health': { target: apiTarget, changeOrigin: true },
      '/sfu-health': { target: apiTarget, changeOrigin: true },
      '/ws': { target: wsTarget, ws: true, changeOrigin: true }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 8088,
    strictPort: false,
    allowedHosts: true,
    cors: true
  }
});
