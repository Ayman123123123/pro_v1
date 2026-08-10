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
    allowedHosts: true,
    proxy: {
      '/api': { target: 'http://backend:8080', changeOrigin: true },
      '/health': { target: 'http://backend:8080', changeOrigin: true },
      '/ws': { target: 'ws://backend:8080', ws: true }
    }
  }
});
