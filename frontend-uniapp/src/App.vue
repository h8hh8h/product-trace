<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { buildDetailUrl, parseH5Location, setPendingScene } from './common/scene.js'

onLaunch(() => {
  // #ifdef H5
  // H5 扫码短链落地：/p/123、/scan/123 需要转到详情页路由；
  // 直接访问 /pages/product/detail 时不再重定向，避免重复请求防伪接口。
  const pathname = (typeof window !== 'undefined' && window.location.pathname) || ''
  if (pathname.indexOf('/pages/product/detail') < 0) {
    const scene = parseH5Location()
    if (scene.productId || scene.code) {
      // 先暂存，详情页 onLoad 兜底读取（避免 router 尚未就绪）
      setPendingScene(scene)
      setTimeout(() => {
        uni.reLaunch({ url: buildDetailUrl(scene) })
      }, 0)
    }
  }
  // #endif
})
</script>

<style>
page {
  background-color: #f5f6f8;
  color: #1f2937;
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica,
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

/* #ifdef H5 */
html,
body {
  background-color: #f5f6f8;
  margin: 0;
  padding: 0;
}
/* #endif */

view,
text,
image,
video,
swiper,
swiper-item {
  box-sizing: border-box;
}
</style>
