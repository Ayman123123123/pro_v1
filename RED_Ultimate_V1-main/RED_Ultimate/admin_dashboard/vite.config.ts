import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import tailwindcss from '@tailwindcss/vite';
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
    tailwindcss(),
    checker({
      typescript: true,
      eslint: { lintCommand: 'eslint src --ext ts,tsx' },
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
      '@/routes': path.resolve(__dirname, './src/routes'),
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
          'vendor-router': ['@tanstack/react-router'],
          'vendor-query': ['@tanstack/react-query'],
          'vendor-table': ['@tanstack/react-table'],
          'vendor-state': ['zustand', 'jotai'],
          'vendor-ui': ['@radix-ui/react-dialog', '@radix-ui/react-dropdown-menu', '@radix-ui/react-select', '@radix-ui/react-toast', '@radix-ui/react-tooltip', '@radix-ui/react-avatar', '@radix-ui/react-label', '@radix-ui/react-switch', '@radix-ui/react-tabs', '@radix-ui/react-popover', '@radix-ui/react-hover-card', '@radix-ui/react-context-menu', '@radix-ui/react-menubar', '@radix-ui/react-navigation-menu', '@radix-ui/react-accordion', '@radix-ui/react-alert-dialog', '@radix-ui/react-checkbox', '@radix-ui/react-collapsible', '@radix-ui/react-progress', '@radix-ui/react-radio-group', '@radix-ui/react-scroll-area', '@radix-ui/react-separator', '@radix-ui/react-slider', '@radix-ui/react-slot'],
          'vendor-charts': ['recharts', '@visx/axis', '@visx/grid', '@visx/group', '@visx/hierarchy', '@visx/mock-data', '@visx/responsive', '@visx/scale', '@visx/shape', '@visx/tooltip'],
          'vendor-forms': ['react-hook-form', 'zod', '@hookform/resolvers'],
          'vendor-i18n': ['i18next', 'react-i18next'],
          'vendor-utils': ['date-fns', 'date-fns-tz', 'clsx', 'tailwind-merge', 'class-variance-authority', 'lucide-react', 'sonner', 'vaul', 'cmdk'],
          'vendor-realtime': ['socket.io-client'],
          'vendor-dnd': ['@dnd-kit/core', '@dnd-kit/sortable', '@dnd-kit/utilities'],
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