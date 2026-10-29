<template>
  <view class="skeleton">
    <!-- 顶部轮播骨架 -->
    <view class="skeleton__banner shimmer"></view>

    <!-- 标题信息骨架 -->
    <view class="skeleton__card">
      <view class="skeleton__row">
        <view class="skeleton__line shimmer line-xs"></view>
        <view class="skeleton__badge shimmer"></view>
      </view>
      <view class="skeleton__line shimmer line-lg"></view>
      <view class="skeleton__line shimmer line-md"></view>
      <view class="skeleton__line shimmer line-sm"></view>
    </view>

    <!-- 视频骨架 -->
    <view class="skeleton__card">
      <view class="skeleton__line shimmer line-title"></view>
      <view class="skeleton__video shimmer"></view>
    </view>

    <!-- 富文本骨架 -->
    <view class="skeleton__card">
      <view class="skeleton__line shimmer line-title"></view>
      <view class="skeleton__line shimmer line-full"></view>
      <view class="skeleton__line shimmer line-full"></view>
      <view class="skeleton__line shimmer line-md"></view>
      <view class="skeleton__line shimmer line-full"></view>
      <view class="skeleton__line shimmer line-sm"></view>
    </view>
  </view>
</template>

<script setup>
// 纯展示组件，无逻辑
</script>

<style scoped>
.skeleton {
  padding-bottom: 48rpx;
}

.skeleton__banner {
  width: 100%;
  height: 720rpx;
}

.skeleton__card {
  margin: 24rpx;
  padding: 28rpx;
  background-color: #ffffff;
  border-radius: 20rpx;
}

.skeleton__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.skeleton__line {
  height: 28rpx;
  border-radius: 8rpx;
  margin-bottom: 20rpx;
}

.skeleton__line:last-child {
  margin-bottom: 0;
}

.line-xs {
  width: 120rpx;
  height: 24rpx;
}

.line-sm {
  width: 40%;
}

.line-md {
  width: 60%;
}

.line-lg {
  width: 82%;
  height: 40rpx;
}

.line-full {
  width: 100%;
}

.line-title {
  width: 180rpx;
  height: 32rpx;
  margin-bottom: 28rpx;
}

.skeleton__badge {
  width: 96rpx;
  height: 34rpx;
  border-radius: 17rpx;
}

.skeleton__video {
  width: 100%;
  height: 380rpx;
  border-radius: 12rpx;
}

/* 流光动画 */
.shimmer {
  background-color: #eceef2;
  background-image: linear-gradient(90deg, #eceef2 25%, #f8f9fb 37%, #eceef2 63%);
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

/* #ifdef H5 */
.skeleton__banner {
  height: 56vw;
  max-height: 560px;
  min-height: 300px;
}
/* #endif */
</style>
