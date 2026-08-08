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
    // Inside the Docker network the backend is reachable as "backend:8080".
    // For local development outside Compose, point RED_API_TARGET at it, e.g.:
    //   RED_API_TARGET=http://localhost:8080 npm run dev
    proxy: {
      '/api': { target: process.env.RED_API_TARGET || 'http://backend:8080', changeOrigin: true },
      '/health': { target: process.env.RED_API_TARGET || 'http://backend:8080', changeOrigin: true },
      '/ws': { target: (process.env.RED_API_TARGET || 'http://backend:8080').replace(/^http/, 'ws'), ws: true }
    }
  }
});
