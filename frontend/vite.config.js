import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxies /api requests to the Spring Boot backend during development,
// so the browser talks to a single origin (no CORS needed in dev).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
})
