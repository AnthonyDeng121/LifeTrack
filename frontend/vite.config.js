import { defineConfig } from 'vite'
import uniPlugin from '@dcloudio/vite-plugin-uni'

const uni = uniPlugin.default || uniPlugin

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    uni(),
  ],
})
