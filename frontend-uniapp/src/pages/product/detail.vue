<template>
  <view class="page">
    <!-- 防伪悬浮提示 -->
    <verify-banner
      :visible="bannerVisible"
      :status="bannerStatus"
      :title="bannerTitle"
      :message="bannerMessage"
      :scan-count="bannerScanCount"
      :scan-limit="bannerScanLimit"
      :top="bannerTop"
      @close="closeBanner"
    />

    <!-- 自定义导航栏 -->
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav__inner">
        <view class="nav__back" @click="goBack">
          <text class="nav__back-icon">‹</text>
        </view>
        <text class="nav__title">{{ product.productName || '产品详情' }}</text>
        <!-- #ifdef MP-WEIXIN -->
        <button class="nav__share" open-type="share">···</button>
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <view class="nav__share" @click="handleShareTap">
          <text class="nav__share-text">···</text>
        </view>
        <!-- #endif -->
      </view>
    </view>

    <!-- 骨架屏 -->
    <view v-if="loading" class="skeleton-wrap" :style="{ paddingTop: navHeight + 'px' }">
      <skeleton-detail />
    </view>

    <!-- 加载失败 -->
    <view v-else-if="errorMessage" class="state" :style="{ paddingTop: navHeight + 80 + 'px' }">
      <view class="state__icon">!</view>
      <text class="state__title">页面加载失败</text>
      <text class="state__message">{{ errorMessage }}</text>
      <view class="state__btns">
        <view class="state__btn state__btn--primary" @click="retry">重新加载</view>
        <view class="state__btn state__btn--ghost" @click="goHome">返回首页</view>
      </view>
    </view>

    <!-- 产品详情 -->
    <block v-else>
      <!-- 顶部图片轮播 -->
      <view class="hero">
        <swiper
          class="hero__swiper"
          :indicator-dots="false"
          :autoplay="images.length > 1"
          :interval="4000"
          :duration="400"
          :circular="images.length > 1"
          @change="onSwiperChange"
        >
          <swiper-item v-for="(img, index) in images" :key="index">
            <image
              class="hero__image"
              :src="img"
              mode="aspectFit"
              @click="previewImage(index)"
            />
          </swiper-item>
        </swiper>

        <view v-if="images.length > 1" class="hero__counter">
          {{ current }} / {{ images.length }}
        </view>
        <view v-if="!images.length" class="hero__empty">
          <text>暂无产品图片</text>
        </view>
      </view>

      <!-- 基础信息 -->
      <view class="card card--overlap">
        <view class="card__head">
          <text v-if="product.brand" class="brand">{{ product.brand }}</text>
          <text class="tag" :class="{ 'tag--risk': hasFakeWarning }">
            {{ hasFakeWarning ? '扫码异常' : '官方正品' }}
          </text>
        </view>

        <text class="product-name">{{ product.productName }}</text>

        <!-- 规格参数：字段与顺序由后台「展示项」配置控制，未填写内容的字段自动隐藏 -->
        <view v-if="visibleSpecs.length" class="spec-list">
          <view
            v-for="item in visibleSpecs"
            :key="item.key"
            class="spec-row"
            :class="{ 'spec-row--full': item.full }"
          >
            <text class="spec-row__label">{{ item.label }}</text>
            <text class="spec-row__value">{{ item.value }}</text>
          </view>
        </view>

        <view v-if="code" class="code-row">
          <text class="code-row__label">溯源码</text>
          <text class="code-row__value">{{ code }}</text>
        </view>
      </view>

      <!-- 视频 -->
      <view v-if="product.videoUrl" class="card">
        <view class="section-title">
          <view class="section-title__bar"></view>
          <text>产品视频</text>
        </view>
        <video
          class="video"
          :style="videoStyle"
          :src="resolveAssetUrl(product.videoUrl)"
          :poster="images[0] || ''"
          :controls="true"
          :autoplay="true"
          :muted="true"
          :show-center-play-btn="true"
          :playsinline="true"
          object-fit="contain"
          @loadedmetadata="onVideoLoadedMetadata"
          @error="onVideoError"
        />
        <text v-if="videoError" class="video-tip">视频加载失败，请检查网络</text>
      </view>

      <!-- 富文本详情 -->
      <view v-if="descriptionHtml" class="card">
        <view class="section-title">
          <view class="section-title__bar"></view>
          <text>产品详情</text>
        </view>
        <rich-text class="rich" :nodes="descriptionHtml" />
      </view>

      <!-- 底部 -->
      <view class="footer">
        <!-- #ifdef MP-WEIXIN -->
        <button class="share-btn" open-type="share">分享给好友</button>
        <!-- #endif -->
        <text class="footer__text">本页面仅用于产品信息展示与防伪验证，不提供在线购买</text>
        <text class="footer__brand">产品数字化展示与一物一码溯源系统</text>
      </view>
    </block>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShareAppMessage, onShareTimeline, onUnload } from '@dcloudio/uni-app'
import SkeletonDetail from '../../components/SkeletonDetail.vue'
import VerifyBanner from '../../components/VerifyBanner.vue'
import { get, request } from '../../common/request.js'
import { resolveAssetUrl } from '../../common/config.js'
import { resolveScene } from '../../common/scene.js'
import { initWxShare } from '../../common/wx-share.js'

/** 骨架屏最短展示时长，避免闪屏 */
const SKELETON_MIN_DURATION = 500
const NAV_CONTENT_HEIGHT = 44

const systemInfo = uni.getSystemInfoSync ? uni.getSystemInfoSync() : {}
const statusBarHeight = ref(systemInfo.statusBarHeight || 0)
const navHeight = computed(() => statusBarHeight.value + NAV_CONTENT_HEIGHT)
const bannerTop = computed(() => navHeight.value + 10)

const product = ref({})
const images = ref([])
const descriptionHtml = ref('')
const current = ref(1)
const loading = ref(true)
const errorMessage = ref('')
const videoError = ref(false)
/** 视频宽高比（width / height），由 loadedmetadata 得到，用于让外框跟随视频比例 */
const videoRatio = ref(0)

const productId = ref('')
const code = ref('')

// 产品信息展示字段：与后台「展示项」配置的 key 一一对应
const SPEC_FIELDS = [
  { key: 'productNo', label: '货号' },
  { key: 'productName', label: '品名' },
  { key: 'brand', label: '品牌' },
  { key: 'crotchType', label: '裆型' },
  { key: 'thickness', label: '厚度' },
  { key: 'color', label: '颜色' },
  { key: 'size', label: '尺码' },
  { key: 'spec', label: '规格' },
  { key: 'material', label: '材质' },
  { key: 'composition', label: '成分' },
  { key: 'retailPrice', label: '零售价' },
  { key: 'feature', label: '特点', full: true },
  { key: 'origin', label: '产地' },
  { key: 'category', label: '分类' },
  { key: 'sortOrder', label: '排序' },
  { key: 'createTime', label: '创建时间', full: true }
]
// 未配置时默认展示除「排序 / 创建时间」以外的字段
const DEFAULT_SPEC_KEYS = SPEC_FIELDS
  .filter(f => f.key !== 'sortOrder' && f.key !== 'createTime')
  .map(f => f.key)

function parseDisplayFields(raw) {
  try {
    const arr = JSON.parse(raw || '[]')
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

const visibleSpecs = computed(() => {
  const saved = parseDisplayFields(product.value.displayFields)
  const allowed = new Set(saved.length ? saved : DEFAULT_SPEC_KEYS)
  return SPEC_FIELDS
    .filter(f => allowed.has(f.key))
    .map(f => {
      let value = product.value[f.key]
      if (f.key === 'createTime' && value) {
        value = String(value).replace('T', ' ').slice(0, 19)
      }
      if (f.key === 'retailPrice' && value !== null && value !== undefined && value !== '') {
        const num = Number(value)
        if (!Number.isNaN(num)) value = '¥' + num.toFixed(2)
      }
      return { ...f, value }
    })
    .filter(f => f.value !== null && f.value !== undefined && String(f.value).trim() !== '')
})

const bannerVisible = ref(false)
const bannerStatus = ref('valid')
const bannerTitle = ref('')
const bannerMessage = ref('')
const bannerScanCount = ref(0)
const bannerScanLimit = ref(0)
let bannerTimer = null
let verifyFailureMessage = ''

const hasFakeWarning = computed(() => bannerVisible.value && bannerStatus.value === 'fake')

onLoad((options) => {
  init(resolveScene(options || {}))
})

onShareAppMessage(() => {
  const share = shareInfo()
  return {
    title: share.title,
    path: share.path,
    imageUrl: share.imageUrl
  }
})

onShareTimeline(() => {
  const share = shareInfo()
  return {
    title: share.title,
    query: share.query,
    imageUrl: share.imageUrl
  }
})

onUnload(() => {
  clearTimeout(bannerTimer)
})

async function init(scene) {
  loading.value = true
  errorMessage.value = ''
  videoError.value = false
  productId.value = scene.productId ? String(scene.productId) : ''
  code.value = scene.code ? String(scene.code) : ''

  const startedAt = Date.now()
  verifyFailureMessage = ''
  try {
    // 先做防伪校验：一物一码场景可以只带 code
    if (code.value) {
      await verifyCode()
    }

    if (!productId.value) {
      throw new Error(verifyFailureMessage || '缺少产品参数，请重新扫描产品二维码')
    }

    const body = await get(`/product/${productId.value}`)
    if (body.code !== 200 || !body.data) {
      throw new Error(body.message || '产品不存在或已下架')
    }

    applyProduct(body.data)
    setupShare()
  } catch (e) {
    errorMessage.value = e.message || '加载失败，请稍后重试'
  } finally {
    const remain = SKELETON_MIN_DURATION - (Date.now() - startedAt)
    if (remain > 0) await sleep(remain)
    loading.value = false
  }
}

/** 调用防伪验证接口，并在顶部弹出对应提示 */
async function verifyCode() {
  try {
    const body = await request({ url: `/verify/${encodeURIComponent(code.value)}` })
    const data = body.data || {}

    if (!productId.value) {
      const pid = data.productId || body.productId
      if (pid) productId.value = String(pid)
    }

    if (body.code === 1004 || data.fakeWarning) {
      bannerScanCount.value = Number(data.scanCount || 0)
      bannerScanLimit.value = Number(data.scanLimit || 0)
      showBanner('fake', '假货预警', data.message || '该码扫码次数异常，请谨慎购买', true)
      return
    }

    if (body.code === 200 && data.valid) {
      bannerScanCount.value = Number(data.scanCount || 0)
      bannerScanLimit.value = Number(data.scanLimit || 0)
      showBanner('valid', '正品验证通过', data.message || '该产品为官方正品', false)
      return
    }

    verifyFailureMessage = body.message || '产品码无效或已失效'
    showBanner('error', '验证未通过', verifyFailureMessage, false)
  } catch (e) {
    verifyFailureMessage = e.message || '防伪校验失败'
    if (e.code === 429) {
      showBanner('error', '访问过于频繁', '请稍后再试，避免短时间内重复扫码', false)
    } else {
      showBanner('error', '防伪校验失败', e.message || '请检查网络后重试', false)
    }
  }
}

function showBanner(status, title, message, persistent) {
  clearTimeout(bannerTimer)
  bannerStatus.value = status
  bannerTitle.value = title
  bannerMessage.value = message
  bannerVisible.value = true

  if (!persistent) {
    bannerTimer = setTimeout(() => {
      bannerVisible.value = false
    }, status === 'valid' ? 4000 : 5000)
  }
}

function closeBanner() {
  clearTimeout(bannerTimer)
  bannerVisible.value = false
}

function applyProduct(data) {
  product.value = data
  videoError.value = false

  const list = []
  const push = (url) => {
    const resolved = resolveAssetUrl(url)
    if (resolved && !list.includes(resolved)) list.push(resolved)
  }
  push(data.coverImage)
  parseImageList(data.imageList).forEach(push)
  images.value = list
  current.value = 1

  descriptionHtml.value = normalizeRichText(data.description)

  uni.setNavigationBarTitle({ title: data.productName || '产品详情' })
}

function parseImageList(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.filter(Boolean)
  const text = String(raw).trim()
  if (!text) return []

  if (text.startsWith('[')) {
    try {
      const parsed = JSON.parse(text)
      if (Array.isArray(parsed)) return parsed.filter(Boolean)
    } catch (e) {
      // 非合法 JSON，继续按分隔符处理
    }
  }
  return text
    .split(/[,，\n]/)
    .map((item) => item.trim().replace(/^["']|["']$/g, ''))
    .filter(Boolean)
}

/** 富文本里的相对图片地址补全，并限制图片宽度 */
function normalizeRichText(html) {
  if (!html) return ''
  return String(html)
    .replace(/(<img[^>]*?)src=["']([^"']+)["']/gi, (match, prefix, src) => {
      return `${prefix}src="${resolveAssetUrl(src)}"`
    })
    // 宽度 100%、高度自适应、块级展示；图片之间留间距防止粘连
    .replace(/<img/gi, '<img style="width:100%;max-width:100%;height:auto;display:block;margin:16rpx 0;border-radius:8rpx;"')
}

function shareInfo() {
  const p = product.value || {}
  const query = []
  if (p.id || productId.value) query.push(`id=${encodeURIComponent(p.id || productId.value)}`)
  if (code.value) query.push(`code=${encodeURIComponent(code.value)}`)

  return {
    title: p.productName ? `${p.productName}｜官方正品溯源` : '产品溯源',
    desc: p.brand ? `${p.brand} · 扫码验证正品` : '扫码验证产品真伪，查看产品详情',
    path: `/pages/product/detail?${query.join('&')}`,
    query: query.join('&'),
    imageUrl: images.value[0] || ''
  }
}

function setupShare() {
  // #ifdef MP-WEIXIN
  // onShareAppMessage / onShareTimeline 已在上方注册，这里无需额外处理
  // #endif

  // #ifdef H5
  const share = shareInfo()
  initWxShare({
    title: share.title,
    desc: share.desc,
    link: window.location.href.split('#')[0],
    imgUrl: share.imageUrl
  })
  // #endif
}

function onSwiperChange(event) {
  current.value = (event.detail.current || 0) + 1
}

function previewImage(index) {
  if (!images.value.length) return
  uni.previewImage({
    current: images.value[index],
    urls: images.value
  })
}

function onVideoError() {
  videoError.value = true
}

/** 卡片内容区宽度（px）：页面宽度 - 卡片外边距(24rpx*2) - 内边距(28rpx*2) */
function contentWidthPx() {
  const windowWidth = systemInfo.windowWidth || 375
  const pageWidth = Math.min(windowWidth, 750)
  return pageWidth - (104 * pageWidth) / 750
}

/**
 * 视频外框自适应：
 *  - 已知宽高比时：高度 = 内容宽度 / 宽高比（竖屏窄高、横屏宽扁），最高不超过 80vh
 *  - 未知宽高比时：先给 75% 宽度的保守高度，等 metadata 到达后再纠偏
 */
const videoStyle = computed(() => {
  const width = contentWidthPx()
  const maxHeight = (systemInfo.windowHeight || 667) * 0.8
  const ratio = videoRatio.value
  let height = ratio ? width / ratio : width * 0.75
  if (height > maxHeight) height = maxHeight
  return { width: '100%', height: Math.round(height) + 'px', maxHeight: '80vh' }
})

function onVideoLoadedMetadata(event) {
  const detail = (event && event.detail) || {}
  const w = Number(detail.width)
  const h = Number(detail.height)
  if (w > 0 && h > 0) videoRatio.value = w / h
}

function handleShareTap() {
  uni.showToast({
    title: '请点击右上角「···」分享',
    icon: 'none'
  })
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack({ delta: 1 })
    return
  }
  // #ifdef H5
  if (window.history.length > 1) {
    window.history.back()
    return
  }
  // #endif
  // 兜底：打开小程序首页
  uni.reLaunch({ url: '/pages/index/index' })
}

function retry() {
  init({ productId: productId.value, code: code.value })
}

function goHome() {
  uni.reLaunch({ url: '/pages/index/index' })
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  background-color: #f5f6f8;
  padding-bottom: env(safe-area-inset-bottom);
}

/* ============ 导航栏 ============ */
.nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background-color: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  box-shadow: 0 2rpx 0 rgba(15, 23, 42, 0.04);
}

.nav__inner {
  height: 44px;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
}

.nav__back {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav__back-icon {
  font-size: 56rpx;
  line-height: 1;
  color: #1f2937;
  margin-top: -8rpx;
}

.nav__title {
  flex: 1;
  margin: 0 16rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2937;
  text-align: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.nav__share {
  width: 56rpx;
  height: 56rpx;
  padding: 0;
  margin: 0;
  line-height: 1;
  background-color: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 40rpx;
  color: #1f2937;
}

.nav__share::after {
  border: none;
}

.nav__share-text {
  font-size: 40rpx;
  line-height: 1;
  color: #1f2937;
}

/* ============ 轮播 ============ */
.hero {
  position: relative;
  width: 100%;
  height: 720rpx;
  background-color: #e9edf2;
}

.hero__swiper {
  width: 100%;
  height: 100%;
}

.hero__image {
  width: 100%;
  height: 100%;
  background-color: #e9edf2;
}

.hero__counter {
  position: absolute;
  right: 28rpx;
  bottom: 68rpx;
  padding: 6rpx 20rpx;
  border-radius: 24rpx;
  background-color: rgba(15, 23, 42, 0.45);
  color: #ffffff;
  font-size: 22rpx;
}

.hero__empty {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  font-size: 26rpx;
}

/* ============ 卡片 ============ */
.card {
  margin: 24rpx;
  padding: 28rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(15, 23, 42, 0.04);
}

.card--overlap {
  position: relative;
  margin-top: -48rpx;
  z-index: 2;
}

.card__head {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.brand {
  font-size: 24rpx;
  color: #6b7280;
  letter-spacing: 1rpx;
  margin-right: 16rpx;
}

.tag {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  color: #047857;
  background-color: #ecfdf5;
  border: 1rpx solid #a7f3d0;
}

.tag--risk {
  color: #b91c1c;
  background-color: #fee2e2;
  border-color: #fca5a5;
}

.product-name {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  line-height: 52rpx;
  color: #111827;
}

/* ============ 规格参数 ============ */
.spec-list {
  margin-top: 24rpx;
  display: flex;
  flex-wrap: wrap;
}

.spec-row {
  display: flex;
  align-items: flex-start;
  width: 50%;
  padding-right: 16rpx;
  margin-bottom: 14rpx;
  box-sizing: border-box;
}

.spec-row--full {
  width: 100%;
}

.spec-row__label {
  flex: 0 0 auto;
  font-size: 24rpx;
  color: #9ca3af;
  margin-right: 12rpx;
}

.spec-row__value {
  flex: 1;
  font-size: 24rpx;
  color: #4b5563;
  word-break: break-all;
}

.code-row {
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f1f5f9;
  display: flex;
  align-items: center;
}

.code-row__label {
  font-size: 24rpx;
  color: #9ca3af;
  margin-right: 16rpx;
}

.code-row__value {
  font-size: 24rpx;
  color: #374151;
  letter-spacing: 1rpx;
  word-break: break-all;
}

/* ============ 分区标题 ============ */
.section-title {
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
  font-size: 30rpx;
  font-weight: 600;
  color: #111827;
}

.section-title__bar {
  width: 8rpx;
  height: 30rpx;
  border-radius: 4rpx;
  background-color: #0f766e;
  margin-right: 14rpx;
}

/* ============ 视频 ============ */
.video {
  width: 100%;
  max-height: 80vh;
  border-radius: 12rpx;
  background-color: #000000;
  object-fit: contain;
}

.video-tip {
  display: block;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #ef4444;
}

/* ============ 富文本 ============ */
.rich {
  font-size: 28rpx;
  line-height: 1.8;
  color: #374151;
  word-break: break-all;
}

/* ============ 底部 ============ */
.footer {
  padding: 40rpx 48rpx 60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.share-btn {
  width: 100%;
  height: 84rpx;
  line-height: 84rpx;
  margin-bottom: 24rpx;
  border-radius: 42rpx;
  background-color: #0f766e;
  color: #ffffff;
  font-size: 28rpx;
}

.share-btn::after {
  border: none;
}

.footer__text {
  font-size: 22rpx;
  color: #9ca3af;
  text-align: center;
  line-height: 36rpx;
}

.footer__brand {
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #cbd5e1;
}

/* ============ 骨架屏容器 ============ */
.skeleton-wrap {
  width: 100%;
}

/* ============ 加载失败 ============ */
.state {
  padding: 0 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.state__icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background-color: #fee2e2;
  color: #ef4444;
  font-size: 52rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}

.state__title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2937;
}

.state__message {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: #6b7280;
  text-align: center;
  line-height: 40rpx;
}

.state__btn {
  margin-top: 40rpx;
  padding: 18rpx 64rpx;
  border-radius: 40rpx;
  background-color: #0f766e;
  color: #ffffff;
  font-size: 28rpx;
}

/* #ifdef H5 */
/* 桌面浏览器下限制内容宽度，保持移动端阅读体验 */
.page {
  max-width: 750px;
  margin: 0 auto;
}

.nav {
  max-width: 750px;
  left: 50%;
  right: auto;
  width: 100%;
  transform: translateX(-50%);
}

/* rpx 在宽屏下会被放大，限制首屏高度 */
.hero {
  height: 56vw;
  max-height: 560px;
  min-height: 300px;
}
/* #endif */
</style>
