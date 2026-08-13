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
      '/api': { target: apiTarget, changeOrigin: true, timeout: 30000 },
      '/health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
      '/sfu-health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
      '/ws': { target: wsTarget, ws: true, changeOrigin: true, timeout: 10000 }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 8088,
    strictPort: false,
    allowedHosts: true,
    cors: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true, timeout: 30000 },
      '/health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
      '/sfu-health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
      '/ws': { target: wsTarget, ws: true, changeOrigin: true, timeout: 10000 }
    }
  }
});
