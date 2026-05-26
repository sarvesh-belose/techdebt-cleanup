import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 9211,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://localhost:9200',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:9200',
        changeOrigin: true,
      },
    },
  },
});
