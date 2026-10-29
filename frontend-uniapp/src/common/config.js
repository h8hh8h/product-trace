/**
 * 接口与资源地址配置
 *
 * 本地联调：
 *  - H5 浏览器：http://127.0.0.1:8080/api（或 /api 走 Vite proxy）
 *  - 微信开发者工具：需勾选「不校验合法域名」，且把地址改成电脑局域网 IP，
 *    例如 http://192.168.1.10:8080/api（手机预览时必须用局域网 IP）
 *
 * 生产构建：
 *  - H5：VITE_API_BASE_URL=/api（宝塔 Nginx 同域反代）
 *  - 小程序：必须是完整 https URL，vite.config.js 已按 UNI_PLATFORM 自动分流，
 *    这里再加一层平台感知兜底——如果小程序端拿到的是相对路径，强制替换为完整域名。
 */
const STORAGE_KEY = 'TRACE_API_BASE_URL'
const MP_PROD_FALLBACK = 'https://api.zjknitting.com/api'

// import.meta.env 是 Vite 编译时注入的只读变量
const BUILD_BASE_URL = (import.meta.env && import.meta.env.VITE_API_BASE_URL) || ''

// 运行时平台感知兜底：小程序端不能用相对路径
// #ifdef MP-WEIXIN
const isMpPlatform = true
// #endif
// #ifndef MP-WEIXIN
const isMpPlatform = false
// #endif

function sanitizeBaseUrl(raw, forceFullForMp) {
  let value = String(raw || '').trim()
  // 小程序端必须完整 URL，不能以 / 开头
  if (forceFullForMp && value.startsWith('/')) {
    return MP_PROD_FALLBACK
  }
  return value
}

const DEFAULT_BASE_URL = sanitizeBaseUrl(BUILD_BASE_URL, isMpPlatform)
  || 'http://127.0.0.1:8080/api'

export function getBaseUrl() {
  let base = ''
  try {
    base = uni.getStorageSync(STORAGE_KEY) || ''
  } catch (e) {
    base = ''
  }
  return (base || DEFAULT_BASE_URL).replace(/\/+$/, '')
}

/** 运行时切换后端地址（小程序真机联调时可调用） */
export function setBaseUrl(url) {
  if (!url) return
  uni.setStorageSync(STORAGE_KEY, String(url).replace(/\/+$/, ''))
}

/** 后端服务根地址（去掉 /api 上下文） */
export function getServerOrigin() {
  return getBaseUrl().replace(/\/api$/, '')
}

/**
 * 补全相对媒体地址：/upload/2026/10/01/a.jpg -> http://127.0.0.1:8080/upload/...
 * 已是 http(s)/data 开头的地址原样返回。
 */
export function resolveAssetUrl(url) {
  if (!url) return ''
  const value = String(url).trim()
  if (!value) return ''
  if (/^(https?:)?\/\//i.test(value) || value.startsWith('data:') || value.startsWith('blob:')) {
    return value
  }
  return getServerOrigin() + (value.startsWith('/') ? value : '/' + value)
}
