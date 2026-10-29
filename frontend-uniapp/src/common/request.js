/**
 * uni.request Promise 封装
 *
 * 说明：后端统一返回 { code, message, data }，HTTP 状态码在正常业务失败时仍为 200，
 * 因此这里只把 HTTP 非 2xx / 网络异常 / 429 转成 reject，
 * 业务码（如 1004 假货预警）由调用方读取 body.code 判断。
 */
import { getBaseUrl } from './config.js'

function createError(message, code, body) {
  const error = new Error(message)
  error.code = code
  error.body = body
  return error
}

export function request(options = {}) {
  const { url, method = 'GET', data, header = {}, timeout = 15000 } = options
  const fullUrl = /^https?:\/\//i.test(url) ? url : getBaseUrl() + url

  return new Promise((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...header
      },
      success: (res) => {
        const body = res.data && typeof res.data === 'object' ? res.data : {}

        if (res.statusCode === 429 || body.code === 429) {
          reject(createError(body.message || '请求过于频繁，请稍后重试', 429, body))
          return
        }
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(body)
          return
        }
        reject(
          createError(
            body.message || `请求失败（HTTP ${res.statusCode}）`,
            body.code || res.statusCode,
            body
          )
        )
      },
      fail: (err) => {
        reject(createError('网络异常，请确认后端服务已启动', -1, err))
      }
    })
  })
}

export function get(url, data) {
  return request({ url, method: 'GET', data })
}

export function post(url, data) {
  return request({ url, method: 'POST', data })
}
