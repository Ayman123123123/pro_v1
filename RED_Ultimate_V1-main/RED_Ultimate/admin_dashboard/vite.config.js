import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const apiTarget = process.env.RED_API_TARGET || 'http://backend:8080';

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
    allowedHosts: true,
    proxy: {
      '/api': { target: apiTarget, changeOrigin: true },
      '/health': { target: apiTarget, changeOrigin: true },
      '/ws': { target: apiTarget.replace(/^http/, 'ws'), ws: true }
    }
  },
  preview: {
    host: '0.0.0.0',
    allowedHosts: true
  }
});
