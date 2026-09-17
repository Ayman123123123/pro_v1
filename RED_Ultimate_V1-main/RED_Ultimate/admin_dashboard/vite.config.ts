import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

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
      '@/routes': path.resolve(__dirname, './src/routes'),
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
          'vendor-router': ['@tanstack/react-router'],
          'vendor-query': ['@tanstack/react-query'],
          'vendor-table': ['@tanstack/react-table'],
          'vendor-virtual': ['@tanstack/react-virtual'],
          'vendor-charts': ['recharts', '@visx/axis', '@visx/shape', '@visx/scale', '@visx/xychart', '@visx/tooltip', '@visx/gradient', '@visx/group', '@visx/responsive', '@visx/mock-data'],
          'vendor-ui': ['@radix-ui/react-dialog', '@radix-ui/react-dropdown-menu', '@radix-ui/react-select', '@radix-ui/react-tabs', '@radix-ui/react-tooltip', '@radix-ui/react-toast', '@radix-ui/react-popover', '@radix-ui/react-avatar', '@radix-ui/react-label', '@radix-ui/react-switch', '@radix-ui/react-slider', '@radix-ui/react-progress', '@radix-ui/react-checkbox', '@radix-ui/react-radio-group', '@radix-ui/react-separator', '@radix-ui/react-scroll-area', '@radix-ui/react-collapsible', '@radix-ui/react-accordion', '@radix-ui/react-aspect-ratio', '@radix-ui/react-hover-card', '@radix-ui/react-context-menu', '@radix-ui/react-menubar', '@radix-ui/react-navigation-menu', '@radix-ui/react-toggle', '@radix-ui/react-toggle-group', '@radix-ui/react-alert-dialog', '@radix-ui/react-avatar'],
          'vendor-ui-antd': ['antd', '@ant-design/icons'],
          'vendor-forms': ['react-hook-form', '@hookform/resolvers', 'zod'],
          'vendor-i18n': ['i18next', 'react-i18next'],
          'vendor-socket': ['socket.io-client'],
          'vendor-utils': ['date-fns', 'clsx', 'tailwind-merge', 'class-variance-authority', 'lucide-react', 'cmdk', 'vaul', 'embla-carousel-react', 'react-day-picker', 'react-resizable-panels'],
        },
        chunkFileNames: 'assets/js/[name]-[hash].js',
        entryFileNames: 'assets/js/[name]-[hash].js',
        assetFileNames: (assetInfo) => {
          const name = assetInfo.name || '';
          const info = name.split('.');
          const ext = info[info.length - 1];
          if (/\.(png|jpe?g|gif|svg|webp|avif|ico)$/.test(name)) {
            return `assets/images/[name]-[hash].${ext}`;
          }
          if (/\.(woff2?|ttf|eot)$/.test(name)) {
            return `assets/fonts/[name]-[hash].${ext}`;
          }
          if (/\.css$/.test(name)) {
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