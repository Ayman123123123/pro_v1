import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  build: {
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
    allowedHosts: 'all',
    cors: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/health': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/sfu-health': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/ws': { target: 'ws://127.0.0.1:8088', ws: true }
    }
  },
  preview: {
    host: '0.0.0.0',
    port: 8088,
    strictPort: false,
    allowedHosts: 'all',
    cors: true,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/health': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/sfu-health': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/ws': { target: 'ws://127.0.0.1:8088', ws: true }
    }
  }
});
