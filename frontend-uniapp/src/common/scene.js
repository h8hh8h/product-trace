/**
 * 扫码场景解析
 *
 * 支持：
 *  - 小程序 wxacode.getUnlimited：onLoad(options).scene = "productId=123"
 *  - 小程序分享：/pages/product/detail?id=123&code=xxx
 *  - H5 短链：/p/123、/scan/123、?id=123、?code=xxx
 *  - H5 / 小程序通用：scene 参数中携带完整 URL
 */
const PENDING_KEY = 'TRACE_PENDING_SCENE'
const ID_KEYS = ['productId', 'id', 'p', 'pid']
const CODE_KEYS = ['code']

/** 最多解码两次，兼容小程序 scene 的二次编码 */
export function safeDecode(value) {
  if (value === undefined || value === null) return ''
  let result = String(value)
  for (let i = 0; i < 2; i++) {
    try {
      const decoded = decodeURIComponent(result)
      if (decoded === result) break
      result = decoded
    } catch (e) {
      break
    }
  }
  return result
}

function extractFromQuery(queryString) {
  const result = {}
  if (!queryString) return result

  // 优先用 URLSearchParams（H5 / 小程序基础库均支持）
  try {
    const params = new URLSearchParams(queryString)
    for (const key of ID_KEYS) {
      const value = params.get(key)
      if (value) {
        result.productId = value
        break
      }
    }
    for (const key of CODE_KEYS) {
      const value = params.get(key)
      if (value) {
        result.code = value
        break
      }
    }
    if (result.productId || result.code) return result
  } catch (e) {
    // 降级手动解析
  }

  queryString.split('&').forEach((pair) => {
    const index = pair.indexOf('=')
    if (index <= 0) return
    const key = pair.slice(0, index)
    const value = safeDecode(pair.slice(index + 1))
    if (ID_KEYS.includes(key) && !result.productId) result.productId = value
    if (CODE_KEYS.includes(key) && !result.code) result.code = value
  })
  return result
}

function extractFromOptions(options = {}) {
  const result = {}
  for (const key of ID_KEYS) {
    if (options[key]) {
      result.productId = safeDecode(options[key])
      break
    }
  }
  for (const key of CODE_KEYS) {
    if (options[key]) {
      result.code = safeDecode(options[key])
      break
    }
  }
  return result
}

/**
 * 解析扫一扫 / 长按识别拿到的二维码内容
 *  - 纯码值：ABC123            -> { code: 'ABC123' }
 *  - 一物一码短链：/scan/ABC123 -> { code: 'ABC123' }
 *  - 产品短链：/p/5             -> { productId: '5' }
 *  - 带 query 的链接：?id=5&code=ABC123 -> 按参数解析
 */
export function parseScanResult(text) {
  const raw = safeDecode(text).trim()
  if (!raw) return {}

  if (!/^https?:\/\//i.test(raw) && !raw.includes('/')) {
    return { code: raw }
  }

  const scanMatch = raw.match(/\/scan\/([A-Za-z0-9_-]+)/)
  if (scanMatch) return { code: scanMatch[1] }

  const productMatch = raw.match(/\/p\/([A-Za-z0-9_-]+)/)
  if (productMatch) return { productId: productMatch[1] }

  const queryMatch = raw.match(/\?([^#]+)/)
  if (queryMatch) {
    const fromQuery = extractFromQuery(queryMatch[1])
    if (fromQuery.productId || fromQuery.code) return fromQuery
  }

  return { code: raw }
}

/**
 * 解析小程序 scene 参数
 * 例：productId%3D123、productId=123&code=abc、纯数字 ID、完整 URL
 */
export function parseSceneParam(scene) {
  const raw = safeDecode(scene).trim()
  if (!raw) return {}

  if (/^\d+$/.test(raw)) {
    return { productId: raw }
  }
  if (raw.includes('=') || raw.includes('&')) {
    const fromQuery = extractFromQuery(raw.replace(/^\?/, ''))
    if (fromQuery.productId || fromQuery.code) return fromQuery
  }

  // /scan/{码值} 是一物一码，不能当产品 ID；/p/{id} 才是产品短链
  const scanMatch = raw.match(/\/scan\/([A-Za-z0-9_-]+)/)
  if (scanMatch) {
    const matched = { code: scanMatch[1] }
    const queryMatch = raw.match(/\?([^#]+)/)
    if (queryMatch) {
      const query = extractFromQuery(queryMatch[1])
      if (query.productId) matched.productId = query.productId
    }
    return matched
  }

  const pathMatch = raw.match(/\/p\/([A-Za-z0-9_-]+)/)
  if (pathMatch) {
    const matched = { productId: pathMatch[1] }
    const queryMatch = raw.match(/\?([^#]+)/)
    if (queryMatch) {
      const query = extractFromQuery(queryMatch[1])
      if (query.code) matched.code = query.code
    }
    return matched
  }

  const queryMatch = raw.match(/\?([^#]+)/)
  if (queryMatch) {
    const fromQuery = extractFromQuery(queryMatch[1])
    if (fromQuery.productId || fromQuery.code) return fromQuery
  }

  // 兜底：把 scene 当作一物一码的码值
  return { code: raw }
}

/** H5：从浏览器地址解析（/p/123、/scan/123、query、hash 模式兜底） */
export function parseH5Location() {
  // #ifdef H5
  if (typeof window === 'undefined') return {}
  const { pathname = '', search = '', hash = '' } = window.location
  const result = {}

  const pathMatch = pathname.match(/\/p\/([A-Za-z0-9_-]+)/) || pathname.match(/\/scan\/([A-Za-z0-9_-]+)/)
  if (pathMatch) result.productId = pathMatch[1]

  Object.assign(result, mergeMissing(result, extractFromQuery(search.replace(/^\?/, ''))))

  if (hash) {
    const hashQueryIndex = hash.indexOf('?')
    if (hashQueryIndex >= 0) {
      Object.assign(result, mergeMissing(result, extractFromQuery(hash.slice(hashQueryIndex + 1))))
    }
    const hashPathMatch = hash.match(/\/p\/([A-Za-z0-9_-]+)/)
    if (hashPathMatch && !result.productId) result.productId = hashPathMatch[1]
  }

  if (!result.productId && !result.code && search) {
    try {
      const scene = new URLSearchParams(search).get('scene')
      if (scene) Object.assign(result, mergeMissing(result, parseSceneParam(scene)))
    } catch (e) {
      // ignore
    }
  }

  return result
  // #endif
  // eslint-disable-next-line no-unreachable
  return {}
}

function mergeMissing(target, source) {
  const merged = {}
  Object.keys(source || {}).forEach((key) => {
    if (!target[key]) merged[key] = source[key]
  })
  return merged
}

function mergeScene(target, source) {
  if (!source) return target
  if (source.productId && !target.productId) target.productId = String(source.productId)
  if (source.code && !target.code) target.code = String(source.code)
  return target
}

export function setPendingScene(scene) {
  if (!scene) return
  try {
    uni.setStorageSync(PENDING_KEY, scene)
  } catch (e) {
    // ignore
  }
}

export function getPendingScene() {
  try {
    return uni.getStorageSync(PENDING_KEY) || null
  } catch (e) {
    return null
  }
}

export function clearPendingScene() {
  try {
    uni.removeStorageSync(PENDING_KEY)
  } catch (e) {
    // ignore
  }
}

/**
 * 页面 onLoad 统一入口：合并 query / scene / H5 地址 / 暂存场景
 */
export function resolveScene(options = {}) {
  const scene = {}

  mergeScene(scene, extractFromOptions(options))
  if (options.scene) mergeScene(scene, parseSceneParam(options.scene))
  // 扫普通链接二维码打开小程序时，完整 URL 会放在 q 参数里
  if (options.q) mergeScene(scene, parseSceneParam(options.q))

  // #ifdef H5
  mergeScene(scene, parseH5Location())
  // #endif

  const pending = getPendingScene()
  if (!scene.productId && !scene.code && pending) {
    mergeScene(scene, pending)
  }
  clearPendingScene()

  // 一物一码的码值可能被误当作 ID（纯数字码），交给后端验证接口兜底
  return scene
}

export function buildDetailUrl(scene = {}) {
  const params = []
  if (scene.productId) params.push(`id=${encodeURIComponent(scene.productId)}`)
  if (scene.code) params.push(`code=${encodeURIComponent(scene.code)}`)
  return `/pages/product/detail${params.length ? '?' + params.join('&') : ''}`
}
