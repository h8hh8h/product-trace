<template>
  <div class="code-print">
    <!-- 工具栏：打印时不输出 -->
    <div class="toolbar no-print">
      <el-button icon="Back" @click="$router.back()">返回</el-button>
      <span class="toolbar__label">标签规格：</span>
      <el-radio-group v-model="specKey">
        <el-radio-button value="40x30">40×30mm</el-radio-button>
        <el-radio-button value="50x30">50×30mm</el-radio-button>
        <el-radio-button value="60x40">60×40mm</el-radio-button>
        <el-radio-button value="custom">自定义</el-radio-button>
      </el-radio-group>
      <template v-if="specKey === 'custom'">
        <el-input-number v-model="customW" :min="20" :max="200" size="small" style="width: 110px" />
        <span class="toolbar__unit">mm ×</span>
        <el-input-number v-model="customH" :min="15" :max="200" size="small" style="width: 110px" />
        <span class="toolbar__label">mm</span>
      </template>
      <span class="toolbar__label">排版：</span>
      <el-radio-group v-model="columns">
        <el-radio-button :value="1">单排</el-radio-button>
        <el-radio-button :value="2">双排</el-radio-button>
      </el-radio-group>
      <el-button type="primary" icon="Printer" :disabled="!items.length" @click="doPrint">打印</el-button>
      <el-button type="success" plain icon="Download" :disabled="!items.length" @click="downloadTspl">下载 TSPL</el-button>
      <span class="toolbar__tip">共 {{ items.length }} 枚标签，按所选规格 1:1 预览；打印请在对话框选择对应标签纸张</span>
    </div>

    <div v-if="loading" class="state no-print">加载中...</div>
    <div v-else-if="!items.length" class="state no-print">未找到二维码数据，请从码列表勾选后进入</div>

    <!-- 标签预览 / 打印区 -->
    <div class="sheet">
      <div
        v-for="(row, ri) in labelRows"
        :key="ri"
        class="label-row"
        :style="{ width: pageWidthMm + 'mm', height: specH + 'mm' }"
      >
        <div
          v-for="item in row"
          :key="item.id"
          class="label"
          :style="{ width: specW + 'mm', height: specH + 'mm' }"
        >
          <img
            v-if="item.qrImageUrl"
            :src="resolveMediaUrl(item.qrImageUrl)"
            class="label__qr"
            :style="{ width: qrMm + 'mm', height: qrMm + 'mm' }"
            referrerpolicy="no-referrer"
            @error="onImgError"
            alt=""
          />
          <div v-else class="label__qr label__qr--empty" :style="{ width: qrMm + 'mm', height: qrMm + 'mm' }">无码</div>
          <div class="label__text">
            <div class="label__name">{{ item.productName || '-' }}</div>
            <div class="label__line">货号：{{ item.productNo || '-' }}</div>
            <div class="label__line label__code">{{ item.code }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../request'
import { resolveMediaUrl, onImgError } from '../media'

// H5 扫码短链前缀：与后端 trace.h5.scan-base-url 保持一致（二维码内容 = 前缀 + '/' + 码值）
const SCAN_BASE_URL = (import.meta.env.VITE_SCAN_BASE_URL || 'https://h5.example.com/scan').replace(/\/+$/, '')
/** TSPL 203dpi：1mm ≈ 8 点 */
const DOTS_PER_MM = 8

const route = useRoute()
const items = ref([])
const loading = ref(true)

const specKey = ref('40x30')
const customW = ref(45)
const customH = ref(30)
const columns = ref(1)

const PRESETS = {
  '40x30': { w: 40, h: 30 },
  '50x30': { w: 50, h: 30 },
  '60x40': { w: 60, h: 40 }
}

const specW = computed(() => (specKey.value === 'custom' ? customW.value : PRESETS[specKey.value].w))
const specH = computed(() => (specKey.value === 'custom' ? customH.value : PRESETS[specKey.value].h))
/** 二维码边长（mm）：上下各留 2mm 边距 */
const qrMm = computed(() => Math.max(10, Math.min(specH.value - 4, specW.value * 0.45)))
/** 一页（一行标签）的物理宽度 */
const pageWidthMm = computed(() => specW.value * columns.value)

const labelRows = computed(() => {
  const rows = []
  for (let i = 0; i < items.value.length; i += columns.value) {
    rows.push(items.value.slice(i, i + columns.value))
  }
  return rows
})

async function loadItems() {
  const ids = String(route.query.ids || '')
    .split(',')
    .map(s => Number(s.trim()))
    .filter(n => Number.isFinite(n) && n > 0)
  if (!ids.length) {
    loading.value = false
    return
  }
  const wanted = new Set(ids)
  const found = new Map()
  const size = 200
  let current = 1
  // 复用既有码分页接口（JOIN 产品表，含 code / qrImageUrl / productNo / productName / brand）
  while (wanted.size > 0) {
    const res = await request.get('/admin/code/page', { params: { current, size } })
    const records = res.data.records || []
    for (const r of records) {
      if (wanted.has(r.id)) {
        found.set(r.id, r)
        wanted.delete(r.id)
      }
    }
    const total = Number(res.data.total || 0)
    if (records.length === 0 || current * size >= total) break
    current++
  }
  // 按入参 ids 顺序输出
  items.value = ids.map(id => found.get(id)).filter(Boolean)
  loading.value = false
  if (!items.value.length) {
    ElMessage.warning('未找到所选二维码')
  }
}

function doPrint() {
  window.print()
}

// 动态 @page：纸张尺寸与所选标签规格匹配、页边距 0
let pageStyleEl = null
function updatePageStyle() {
  if (!pageStyleEl) {
    pageStyleEl = document.createElement('style')
    pageStyleEl.id = 'code-print-page-style'
    document.head.appendChild(pageStyleEl)
  }
  pageStyleEl.textContent = `@page { size: ${pageWidthMm.value}mm ${specH.value}mm; margin: 0; }`
}
watch([specW, specH, columns], updatePageStyle)

function scanUrl(code) {
  return `${SCAN_BASE_URL}/${code}`
}

/** TSPL 字符串安全化：去掉双引号、回车换行 */
function ts(s) {
  return String(s ?? '').replace(/["\r\n]/g, ' ').trim()
}

function downloadTspl() {
  const wDots = specW.value * DOTS_PER_MM
  const hDots = specH.value * DOTS_PER_MM
  const margin = 2 * DOTS_PER_MM
  const qrDots = qrMm.value * DOTS_PER_MM
  const textX = margin + qrDots + 2 * DOTS_PER_MM
  const lineH = 4 * DOTS_PER_MM
  const nameMaxChars = Math.max(4, Math.floor((wDots - textX - margin) / 16))

  const lines = []
  for (const row of labelRows.value) {
    lines.push(`SIZE ${pageWidthMm.value} mm,${specH.value} mm`)
    lines.push('GAP 2 mm,0 mm')
    lines.push('CLS')
    row.forEach((item, col) => {
      const offX = col * wDots
      const qrY = Math.max(0, Math.floor((hDots - qrDots) / 2))
      lines.push(`QRCODE ${offX + margin},${qrY},L,5,A,0,M2,S3,"${ts(scanUrl(item.code))}"`)
      const name = ts(item.productName || '-')
      lines.push(`TEXT ${offX + textX},${margin},"0",0,1,1,"${name.length > nameMaxChars ? name.slice(0, nameMaxChars) : name}"`)
      lines.push(`TEXT ${offX + textX},${margin + lineH},"0",0,1,1,"货号:${ts(item.productNo || '-')}"`)
      lines.push(`TEXT ${offX + textX},${margin + lineH * 2},"0",0,1,1,"${ts(item.code)}"`)
    })
    lines.push('PRINT 1,1')
  }

  const blob = new Blob([lines.join('\r\n')], { type: 'text/plain;charset=utf-8' })
  const now = new Date()
  const pad = n => String(n).padStart(2, '0')
  const stamp = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `code-print-${stamp}.prn`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('TSPL 指令文件已生成')
}

onMounted(() => {
  loadItems()
  updatePageStyle()
})
</script>

<style scoped>
.code-print { padding: 12px; }
.toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 16px; }
.toolbar__label { font-size: 13px; color: #606266; }
.toolbar__unit { font-size: 13px; color: #909399; margin-left: -6px; }
.toolbar__tip { font-size: 12px; color: #909399; }
.state { padding: 60px 0; text-align: center; color: #909399; }

.sheet { display: flex; flex-direction: column; gap: 2mm; }
.label-row { display: flex; background: #fff; }
.label {
  display: flex;
  align-items: center;
  gap: 2mm;
  padding: 2mm;
  box-sizing: border-box;
  border: 1px dashed #dcdfe6;
  overflow: hidden;
}
.label__qr { flex-shrink: 0; display: block; }
.label__qr--empty {
  border: 1px dashed #dcdfe6;
  color: #c0c4cc;
  font-size: 2.5mm;
  display: flex;
  align-items: center;
  justify-content: center;
}
.label__text { flex: 1; min-width: 0; line-height: 1.3; overflow: hidden; }
.label__name {
  font-size: 3mm;
  font-weight: 600;
  color: #000;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}
.label__line { font-size: 2.5mm; color: #000; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.label__code { font-family: monospace; }

@media print {
  .no-print { display: none !important; }
  .code-print { padding: 0; }
  .sheet { gap: 0; }
  .label { border: none; }
  .label-row { page-break-after: always; break-after: page; }
  .label-row:last-child { page-break-after: auto; break-after: auto; }
}
</style>
