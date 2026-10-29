<template>
  <view class="page">
    <!-- 顶部固定搜索框 -->
    <view class="search-bar">
      <view class="search-bar__box">
        <text class="search-bar__icon">🔍</text>
        <input
          v-model="keyword"
          class="search-bar__input"
          type="text"
          placeholder="搜索产品名称 / 货号 / 编号"
          confirm-type="search"
          @confirm="doSearch"
        />
        <text v-if="keyword" class="search-bar__clear" @click="resetSearch">✕</text>
      </view>
      <text v-if="keyword" class="search-bar__btn" @click="doSearch">搜索</text>
    </view>

    <!-- Banner 轮播 -->
    <swiper v-if="banner.length" class="banner" autoplay circular :interval="3500" indicator-dots indicator-active-color="#ffffff">
      <swiper-item v-for="(img, i) in banner" :key="i" @click="goScan">
        <image :src="img" class="banner__img" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <!-- 顶部标题区 -->
    <view class="hero" :class="{ 'hero--no-banner': !banner.length }">
      <text class="hero__title">{{ homeTitle }}</text>
      <text v-if="homeSubtitle" class="hero__sub">{{ homeSubtitle }}</text>
      <view class="hero__scan" @click="goScan">
        <view class="hero__scan-icon">扫</view>
        <text class="hero__scan-text">扫码验证</text>
      </view>
    </view>

    <!-- 产品列表（全部上架产品，推荐置顶） -->
    <view v-if="products.length" class="section">
      <view class="section__title">
        <view class="section__bar"></view>
        <text>{{ keyword ? `搜索结果（${products.length}）` : '全部产品' }}</text>
      </view>
      <view class="grid">
        <view v-for="p in products" :key="p.id" class="card" @click="goDetail(p.id)">
          <image :src="p.coverImage" class="card__cover" mode="aspectFill" />
          <view class="card__body">
            <text class="card__no">{{ p.productNo }}</text>
            <text class="card__name">{{ p.productName }}</text>
            <view class="card__meta">
              <text v-if="isRecommend(p.id)" class="card__tag card__tag--hot">推荐</text>
              <text v-if="p.brand" class="card__tag">{{ p.brand }}</text>
              <text v-if="p.category" class="card__tag">{{ p.category }}</text>
            </view>
            <view v-if="p.retailPrice != null" class="card__price">¥{{ Number(p.retailPrice).toFixed(2) }}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 空态 -->
    <view v-else class="empty">
      <text class="empty__text">{{ keyword ? '未找到匹配的产品，换个关键词试试' : '暂无上架产品' }}</text>
      <text v-if="keyword" class="empty__reset" @click="resetSearch">清空搜索</text>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { get } from '../../common/request.js'
import { resolveAssetUrl } from '../../common/config.js'
import { buildDetailUrl, parseScanResult } from '../../common/scene.js'

const homeTitle = ref('产品溯源 · 品质保证')
const homeSubtitle = ref('')
const banner = ref([])
const products = ref([])
const recommendIds = ref([])
const keyword = ref('')
const searching = ref(false)

function mapProduct(p) {
  return { ...p, coverImage: resolveAssetUrl(p.coverImage) }
}

function isRecommend(id) {
  return recommendIds.value.includes(id)
}

async function load() {
  try {
    const r = await get('/site/home')
    if (r.code === 200 && r.data) {
      homeTitle.value = r.data.homeTitle || homeTitle.value
      homeSubtitle.value = r.data.homeSubtitle || ''
      // 相对路径（/upload/xxx）补全为后端完整地址；完整 http(s) 地址原样保留
      banner.value = (r.data.homeBanner || []).map(resolveAssetUrl).filter(Boolean)
      // 推荐产品 id 顺序（用于打标）
      recommendIds.value = (r.data.recommend || []).map(p => p.id)
      // 全量上架产品（后端已推荐置顶排序）
      products.value = (r.data.products || r.data.recommend || []).map(mapProduct)
      uni.setNavigationBarTitle({ title: r.data.siteName || '产品溯源' })
    }
  } catch (e) {
    console.warn('load home failed', e)
  }
}

async function doSearch() {
  const kw = keyword.value.trim()
  if (!kw) return resetSearch()
  searching.value = true
  try {
    const r = await get('/site/products', { keyword: kw })
    if (r.code === 200 && r.data) {
      products.value = (r.data.products || []).map(mapProduct)
    }
  } catch (e) {
    console.warn('search failed', e)
  } finally {
    searching.value = false
  }
}

function resetSearch() {
  keyword.value = ''
  load()
}

function goDetail(id) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}

function goScan() {
  // #ifdef MP-WEIXIN
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      // 二维码可能是纯码值，也可能是 /scan/码值 短链，统一解析后再跳转
      const scene = parseScanResult(res.result || '')
      if (!scene.code && !scene.productId) {
        uni.showToast({ title: '未识别到有效的产品二维码', icon: 'none' })
        return
      }
      uni.navigateTo({ url: buildDetailUrl(scene) })
    }
  })
  // #endif
  // #ifdef H5
  uni.showToast({ title: '请使用微信扫一扫', icon: 'none' })
  // #endif
}

onMounted(load)
</script>

<style scoped>
.page { min-height: 100vh; background-color: #f5f6f8; padding-bottom: 40rpx; }

/* 顶部固定搜索框 */
.search-bar {
  position: sticky; top: 0; z-index: 100;
  display: flex; align-items: center; gap: 16rpx;
  padding: 16rpx 24rpx; background: #fff;
  box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.06);
}
.search-bar__box {
  flex: 1; display: flex; align-items: center; gap: 12rpx;
  background: #f2f3f5; border-radius: 999rpx; padding: 12rpx 24rpx;
}
.search-bar__icon { font-size: 26rpx; color: #9ca3af; }
.search-bar__input { flex: 1; font-size: 26rpx; color: #1f2937; background: transparent; }
.search-bar__clear { font-size: 26rpx; color: #9ca3af; padding: 0 8rpx; }
.search-bar__btn { font-size: 28rpx; color: #12806d; font-weight: 600; flex: none; }

/* Banner：16:5 比例铺满，cover 不拉伸 */
.banner { width: 100%; height: 234rpx; background: #f0f2f5; }
.banner__img { width: 100%; height: 100%; display: block; }

/* Hero */
.hero { padding: 40rpx 32rpx 30rpx; background: linear-gradient(180deg, #eaf6f4 0%, #f5f6f8 100%); }
.hero--no-banner { padding-top: 80rpx; }
.hero__title { display: block; font-size: 44rpx; font-weight: 700; color: #0f2d2a; line-height: 1.4; }
.hero__sub { display: block; margin-top: 12rpx; font-size: 26rpx; color: #5a6764; }
.hero__scan {
  margin-top: 28rpx; display: inline-flex; align-items: center; gap: 10rpx;
  padding: 16rpx 28rpx; border-radius: 999rpx; background: #12806d; color: #fff;
  box-shadow: 0 8rpx 20rpx rgba(18,128,109,0.25);
}
.hero__scan-icon {
  width: 40rpx; height: 40rpx; border-radius: 50%; background: rgba(255,255,255,0.25);
  display: flex; align-items: center; justify-content: center; font-size: 22rpx; font-weight: 700;
}
.hero__scan-text { font-size: 26rpx; font-weight: 500; }

/* 区块 */
.section { padding: 0 24rpx; margin-top: 20rpx; }
.section__title { display: flex; align-items: center; gap: 12rpx; margin: 24rpx 8rpx 20rpx; font-size: 30rpx; font-weight: 600; color: #303133; }
.section__bar { width: 6rpx; height: 28rpx; background: #12806d; border-radius: 4rpx; }

/* 产品网格 */
.grid { display: flex; flex-wrap: wrap; justify-content: space-between; }
.card {
  width: 344rpx; margin-bottom: 20rpx; background: #fff; border-radius: 14rpx;
  overflow: hidden; box-shadow: 0 4rpx 14rpx rgba(15,23,42,0.06);
}
.card__cover { width: 100%; height: 344rpx; background: #f7f8fa; } /* 卡片宽 344rpx → 封面 1:1 正方形，aspectFill 铺满 */
.card__body { padding: 18rpx 16rpx; }
.card__no { display: block; font-size: 22rpx; color: #9ca3af; margin-bottom: 4rpx; }
.card__name { display: block; font-size: 28rpx; font-weight: 600; color: #1f2937; line-height: 1.35; height: 38rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card__meta { display: flex; gap: 8rpx; flex-wrap: wrap; margin-top: 10rpx; }
.card__tag { font-size: 20rpx; color: #12806d; background: #e8f4f1; padding: 2rpx 10rpx; border-radius: 6rpx; }
.card__tag--hot { color: #e4572e; background: #fdeee8; }
.card__price { margin-top: 12rpx; font-size: 32rpx; font-weight: 700; color: #e4572e; }

.empty { padding: 160rpx 40rpx; text-align: center; color: #9ca3af; font-size: 26rpx; }
.empty__reset { display: inline-block; margin-top: 20rpx; color: #12806d; font-size: 26rpx; }
</style>
