import { configDefaults, defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    exclude: [...configDefaults.exclude, '.local/**', 'android/**'],
    environment: 'jsdom',
    globals: true,
  },
})
