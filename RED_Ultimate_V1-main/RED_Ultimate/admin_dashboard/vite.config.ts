import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import checker from 'vite-plugin-checker';
import path from 'path';

const apiTarget = process.env.RED_API_TARGET || 'http://127.0.0.1:8088';
const wsTarget = apiTarget.replace(/^http/, 'ws');

const proxy = {
  '/api': { target: apiTarget, changeOrigin: true, timeout: 30000 },
  '/health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
  '/sfu-health': { target: apiTarget, changeOrigin: true, timeout: 4000 },
  '/ws': { target: wsTarget, ws: true, changeOrigin: true, timeout: 10000 },
};

export default defineConfig({
  plugins: [
    react(),
    checker({
      typescript: true,
      eslint: { lintCommand: 'eslint src' },
    }),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@/components': path.resolve(__dirname, './src/components'),
      '@/hooks': path.resolve(__dirname, './src/hooks'),
      '@/stores': path.resolve(__dirname, './src/stores'),
      '@/api': path.resolve(__dirname, './src/api'),
      '@/pages': path.resolve(__dirname, './src/pages'),
      '@/utils': path.resolve(__dirname, './src/utils'),
      '@/types': path.resolve(__dirname, './src/types'),
      '@/styles': path.resolve(__dirname, './src/styles'),
    },
  },
  build: {
    target: 'es2022',
    chunkSizeWarningLimit: 1500,
    minify: 'esbuild',
    cssCodeSplit: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-react': ['react', 'react-dom'],
          'vendor-router': ['react-router-dom'],
          'vendor-charts': ['echarts', 'echarts-for-react'],
          'vendor-ui': ['antd', '@ant-design/icons'],
        },
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name.split('.');
          const ext = info[info.length - 1];
          if (/\.(png|jpe?g|gif|svg|webp|avif|ico)$/.test(assetInfo.name)) {
            return `assets/images/[name]-[hash].${ext}`;
          }
          if (/\.(woff2?|ttf|eot)$/.test(assetInfo.name)) {
            return `assets/fonts/[name]-[hash].${ext}`;
          }
          if (/\.css$/.test(assetInfo.name)) {
            return `assets/css/[name]-[hash].${ext}`;
          }
          return `assets/[name]-[hash].${ext}`;
        },
      },
    },
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
  },
});