import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/**
 * uni-app 构建时会注入 UNI_PLATFORM 环境变量：
 *   H5 dev/build    → h5
 *   微信小程序 dev/build → mp-weixin
 *
 * 小程序端 uni.request 不支持相对路径，必须完整 http(s) URL。
 * 所以 H5 生产用 /api（同域反代），小程序生产用完整域名。
 *
 * 真机联调时可在命令行覆盖：
 *   PowerShell:  $env:VITE_API_BASE_URL="http://192.168.1.10:8080/api"; npm run build:mp-weixin
 *   CMD:         set VITE_API_BASE_URL=http://192.168.1.10:8080/api && npm run build:mp-weixin
 */
const platform = process.env.UNI_PLATFORM
const mode = process.env.MODE || 'production'
const PROD_API_DOMAIN = 'https://api.example.com/api'

// 小程序 + 生产构建：强制用完整域名（不能是相对路径 /api）
if (platform === 'mp-weixin' && mode === 'production') {
  const existing = process.env.VITE_API_BASE_URL
  if (!existing || existing.startsWith('/')) {
    console.log(`[vite] mp-weixin production: override VITE_API_BASE_URL -> ${PROD_API_DOMAIN}`)
    process.env.VITE_API_BASE_URL = PROD_API_DOMAIN
  } else {
    console.log(`[vite] mp-weixin production: using existing VITE_API_BASE_URL -> ${existing}`)
  }
}

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
