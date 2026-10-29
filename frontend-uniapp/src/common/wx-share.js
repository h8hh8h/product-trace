/**
 * H5 微信内自定义分享（JS-SDK）
 *
 * 流程：动态加载 jweixin-1.6.0.js -> 后端 /wx/jssdk-config 签名 ->
 *      wx.config -> wx.ready 调用 updateAppMessageShareData / updateTimelineShareData
 *
 * 注意：
 *  1. 仅在微信内置浏览器生效；普通浏览器 / 小程序不加载。
 *  2. 公众号需在「JS 接口安全域名」中配置当前 H5 域名。
 *  3. 分享图片 imgUrl 必须是可公网访问的 https 图片（建议用产品封面）。
 */
import { request } from './request.js'

const SDK_URL = 'https://res.wx.qq.com/open/js/jweixin-1.6.0.js'
let sdkPromise = null

export function isWechat() {
  // #ifdef H5
  return typeof navigator !== 'undefined' && /micromessenger/i.test(navigator.userAgent)
  // #endif
  // eslint-disable-next-line no-unreachable
  return false
}

function loadSdk() {
  // #ifdef H5
  if (typeof window === 'undefined') return Promise.reject(new Error('非浏览器环境'))
  if (window.wx) return Promise.resolve(window.wx)

  if (!sdkPromise) {
    sdkPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SDK_URL
      script.async = true
      script.onload = () => resolve(window.wx)
      script.onerror = () => {
        sdkPromise = null
        reject(new Error('微信 JS-SDK 加载失败'))
      }
      document.head.appendChild(script)
    })
  }
  return sdkPromise
  // #endif
  // eslint-disable-next-line no-unreachable
  return Promise.reject(new Error('仅 H5 支持微信 JS-SDK'))
}

export async function initWxShare({ title, desc, link, imgUrl } = {}) {
  // #ifdef H5
  if (!isWechat()) return { supported: false }

  try {
    const wx = await loadSdk()
    const signUrl = window.location.href.split('#')[0]
    const body = await request({
      url: `/wx/jssdk-config?url=${encodeURIComponent(signUrl)}`
    })

    const config = body.data || {}
    if (!config.signature) {
      throw new Error(body.message || 'JS-SDK 签名获取失败')
    }

    const shareData = {
      title: title || document.title,
      desc: desc || '',
      link: link || signUrl,
      imgUrl: imgUrl || ''
    }

    wx.config({
      debug: false,
      appId: config.appId,
      timestamp: config.timestamp,
      nonceStr: config.nonceStr,
      signature: config.signature,
      jsApiList: config.jsApiList || ['updateAppMessageShareData', 'updateTimelineShareData']
    })

    wx.ready(() => {
      try {
        wx.updateAppMessageShareData(shareData)
        wx.updateTimelineShareData({ title: shareData.title, link: shareData.link, imgUrl: shareData.imgUrl })
      } catch (e) {
        console.warn('[wx-share] 设置分享内容失败', e)
      }
    })

    wx.error((err) => {
      console.warn('[wx-share] wx.config 失败', err && err.errMsg)
    })

    return { supported: true }
  } catch (e) {
    console.warn('[wx-share] 初始化失败：', e.message || e)
    return { supported: true, error: e.message || String(e) }
  }
  // #endif
  // eslint-disable-next-line no-unreachable
  return { supported: false }
}
