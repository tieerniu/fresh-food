import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiBase = env.VITE_API_BASE || '/api'
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    build: {
      cssCodeSplit: false,
      modulePreload: {
        resolveDependencies: () => []
      },
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('echarts') || id.includes('zrender')) {
                return 'charts'
              }
              if (
                  id.includes('element-plus') ||
                  id.includes('@element-plus') ||
                  id.includes('@popperjs') ||
                  id.includes('lodash')
              ) {
                return 'element-plus'
              }
              if (id.includes('vue') || id.includes('vue-router')) {
                return 'vue'
              }
              if (id.includes('axios')) {
                return 'http'
              }
              return 'vendor'
            }
          }
        }
      }
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: true,
      port: 3000, // 前端运行端口
      proxy: {
        [apiBase]: {
          target: apiTarget, // 本地开发时代理到后端
          changeOrigin: true
        }
      }
    }
  }
})
