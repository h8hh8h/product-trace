<template>
  <view
    v-if="visible"
    class="verify"
    :class="'verify--' + status"
    :style="{ top: top + 'px' }"
  >
    <view class="verify__icon">
      <text class="verify__icon-text">{{ iconText }}</text>
    </view>

    <view class="verify__content">
      <text class="verify__title">{{ title }}</text>
      <text class="verify__message">{{ message }}</text>
      <text
        v-if="status === 'valid' && scanLimit > 0"
        class="verify__extra"
      >
        该码已被扫描 {{ scanCount }} / {{ scanLimit }} 次
      </text>
    </view>

    <view class="verify__close" @click="emit('close')">
      <text class="verify__close-text">×</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** valid 正品 | fake 假货预警 | error 校验异常 */
  status: { type: String, default: 'valid' },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  scanCount: { type: Number, default: 0 },
  scanLimit: { type: Number, default: 0 },
  /** 距页面顶部的距离(px)，用于避让自定义导航栏 */
  top: { type: Number, default: 56 }
})

const emit = defineEmits(['close'])

const iconText = computed(() => {
  if (props.status === 'valid') return '✓'
  if (props.status === 'fake') return '!'
  return '!'
})
</script>

<style scoped>
.verify {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  z-index: 999;
  display: flex;
  align-items: flex-start;
  padding: 24rpx 20rpx;
  border-radius: 16rpx;
  border-width: 2rpx;
  border-style: solid;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.12);
  animation: verify-in 0.28s ease-out;
}

/* 正品：绿色 */
.verify--valid {
  background-color: #ecfdf5;
  border-color: #10b981;
}

/* 假货：红色闪烁 */
.verify--fake {
  background-color: #fee2e2;
  border-color: #ef4444;
  animation: verify-in 0.28s ease-out, verify-blink 0.9s ease-in-out infinite;
}

/* 校验异常：橙色 */
.verify--error {
  background-color: #fff7ed;
  border-color: #f59e0b;
}

.verify__icon {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
  margin-right: 16rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.verify--valid .verify__icon {
  background-color: #10b981;
}

.verify--fake .verify__icon {
  background-color: #ef4444;
}

.verify--error .verify__icon {
  background-color: #f59e0b;
}

.verify__icon-text {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1;
}

.verify__content {
  flex: 1;
  min-width: 0;
}

.verify__title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 42rpx;
}

.verify--valid .verify__title {
  color: #047857;
}

.verify--fake .verify__title {
  color: #b91c1c;
}

.verify--error .verify__title {
  color: #b45309;
}

.verify__message {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  line-height: 36rpx;
}

.verify--valid .verify__message {
  color: #059669;
}

.verify--fake .verify__message {
  color: #dc2626;
}

.verify--error .verify__message {
  color: #d97706;
}

.verify__extra {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #059669;
}

.verify__close {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.verify__close-text {
  font-size: 36rpx;
  line-height: 1;
  color: #64748b;
}

@keyframes verify-in {
  0% {
    opacity: 0;
    transform: translateY(-20rpx);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes verify-blink {
  0%,
  100% {
    background-color: #fee2e2;
    box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.55);
  }
  50% {
    background-color: #fecaca;
    box-shadow: 0 0 0 10rpx rgba(239, 68, 68, 0);
  }
}
</style>
