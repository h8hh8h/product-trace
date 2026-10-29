/**
 * 媒体地址解析（统一入口，所有 <img> / <video> 的 src 都必须经过这里）
 *
 * 规则：
 *  1. http:// / https:// / // / data: / blob: 开头的地址 —— 视为完整地址，原样返回，绝不拼接前缀；
 *  2. 其他（如 /upload/2026/10/25/a.jpg）—— 视为本站相对地址：
 *     配置了 VITE_MEDIA_BASE_URL 时拼上该域名（例：https://api.yourdomain.com），
 *     未配置时保持不变，由当前站点的 Nginx location /upload/ 直出。
 */
const MEDIA_BASE = String((import.meta.env && import.meta.env.VITE_MEDIA_BASE_URL) || '')
  .trim()
  .replace(/\/+$/, '')

export function isAbsoluteUrl(url) {
  const value = String(url || '').trim()
  return /^(https?:)?\/\//i.test(value) || value.startsWith('data:') || value.startsWith('blob:')
}

export function resolveMediaUrl(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (isAbsoluteUrl(value)) return value
  const path = value.startsWith('/') ? value : '/' + value
  return MEDIA_BASE ? MEDIA_BASE + path : path
}

/**
 * 保存前收敛用户输入：
 *  - 完整地址原样保留
 *  - 相对地址补成 / 开头
 */
export function normalizeMediaInput(url) {
  const value = String(url || '').trim()
  if (!value) return ''
  if (isAbsoluteUrl(value)) return value
  return value.startsWith('/') ? value : '/' + value
}

/** 图片加载失败占位图（内联 SVG，不依赖任何外部资源） */
export const IMG_FALLBACK =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200"><rect width="100%" height="100%" fill="#f2f3f5"/><text x="50%" y="46%" font-size="14" fill="#909399" text-anchor="middle">图片加载失败</text><text x="50%" y="60%" font-size="11" fill="#c0c4cc" text-anchor="middle">链接无效或被防盗链拦截</text></svg>'
  )

/** <img> 加载失败兜底：换成占位图（防止 onerror 循环触发） */
export function onImgError(e) {
  const el = e && e.target
  if (el && el.src !== IMG_FALLBACK) el.src = IMG_FALLBACK
}

/**
 * 富文本 HTML 注入防盗链绕过：给所有 <img> 加 referrerpolicy="no-referrer"
 * 1688 / 阿里 OSS 等图床检测 Referer，不带 Referer 即可正常加载
 */
export function injectNoReferrer(html) {
  return String(html || '').replace(/<img(?![^>]*referrerpolicy)/gi, '<img referrerpolicy="no-referrer"')
}
