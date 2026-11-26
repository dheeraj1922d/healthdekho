import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// `npm run build` writes the finished site into the Spring Boot jar's static folder,
// so the single jar serves both the API (/api/...) and the web pages.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // `npm run dev` only: forward /api calls to the Spring Boot app running locally.
    proxy: { '/api': 'http://localhost:5000' },
  },
  build: {
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
})
