import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.VITE_PROXY_TARGET || 'http://localhost:8080'
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@refimg': path.resolve(__dirname, '../reference_documents/reference_images'),
      },
    },
    server: {
      fs: {
        allow: [
          path.resolve(__dirname, '.'),
          path.resolve(__dirname, '../reference_documents/reference_images'),
          path.resolve(__dirname, '..'),
        ],
      },
      proxy: {
        '/api': {
          target,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, ''),
        },
      },
    },
  }
})
