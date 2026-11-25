import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发期通过代理把 /api 转发到后端，避免跨域与 Cookie SameSite 问题
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
