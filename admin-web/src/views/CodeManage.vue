<template>
  <div>
    <el-card class="mb">
      <template #header>生成二维码</template>
      <el-form inline>
        <el-form-item label="选择产品">
          <el-select
            v-model="selectedProduct"
            filterable
            remote
            clearable
            placeholder="输入产品编号/名称/品牌搜索"
            :remote-method="searchProducts"
            :loading="productSearchLoading"
            style="width: 360px"
            @change="onProductChange"
          >
            <el-option v-for="p in products" :key="p.id" :label="`${p.productName}（${p.productNo}）${p.brand ? ' / ' + p.brand : ''}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :disabled="!selectedProduct" :loading="normalLoading" @click="genNormal">生成普通产品码</el-button>
        </el-form-item>
        <el-form-item label="批量数量">
          <el-input-number v-model="batchCount" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="扫码上限">
          <el-input-number v-model="batchScanLimit" :min="0" :max="999" />
          <span class="form-hint">0 = 不限，默认 3 次</span>
        </el-form-item>
        <el-form-item>
          <el-button type="success" :disabled="!selectedProduct" :loading="batchLoading" @click="genBatch">批量生成一物一码</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="normalCode" type="success" :closable="false" class="mb">
        <div class="normal-result">
          <el-image :src="resolveMediaUrl(normalResult.qrImageUrl)" referrerpolicy="no-referrer" style="width: 120px; height: 120px; background: #f5f7fa" fit="contain">
            <template #error><div class="qr-img-err">加载失败</div></template>
          </el-image>
          <div>
            <p>码值：{{ normalResult.code }}</p>
            <p>二维码内容：H5 短链（扫码直接打开产品详情页）</p>
            <el-link :href="resolveMediaUrl(normalResult.qrImageUrl)" target="_blank" type="primary">下载二维码图片</el-link>
          </div>
        </div>
      </el-alert>

      <div v-if="batchTask" class="batch-progress">
        <span>批次号：{{ batchTask.batchNo }}　进度：{{ batchTask.generated }}/{{ batchTask.total }}</span>
        <el-progress :percentage="batchPercent" :status="batchTask.status === 'SUCCESS' ? 'success' : ''" style="width: 400px" />
        <el-button v-if="batchTask.status === 'SUCCESS'" type="primary" size="small" @click="downloadCsv">下载 CSV</el-button>
      </div>

      <details class="history-list" style="margin-top: 12px">
        <summary style="cursor: pointer; color: #409eff; font-size: 13px">📋 历史批次（页面刷新后仍保留）</summary>
        <el-table :data="historyBatches" v-loading="historyLoading" size="small" style="margin-top: 8px" border>
          <el-table-column prop="batchNo" label="批次号" width="180" />
          <el-table-column prop="productName" label="产品" min-width="140" />
          <el-table-column prop="total" label="总数" width="70" />
          <el-table-column prop="scanLimit" label="扫码上限" width="80">
            <template #default="{ row }">{{ row.scanLimit === 0 ? '不限' : row.scanLimit }}</template>
          </el-table-column>
          <el-table-column prop="generated" label="已生成" width="80" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="batchStatusTag(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button v-if="row.status === 'SUCCESS'" link type="primary" size="small" @click="downloadHistoryCsv(row)">下载 CSV</el-button>
            </template>
          </el-table-column>
        </el-table>
      </details>
    </el-card>

    <el-card>
      <template #header>码列表</template>
      <div class="toolbar">
        <el-input v-model="codeQuery.keyword" placeholder="编号/名称/分类/品牌/规格/码值" clearable style="width: 240px" @keyup.enter="loadCodes" />
        <el-select v-model="codeQuery.codeType" placeholder="码类型" clearable style="width: 140px" @change="loadCodes">
          <el-option label="普通产品码" :value="1" />
          <el-option label="一物一码" :value="2" />
        </el-select>
        <el-select v-model="codeQuery.status" placeholder="状态" clearable style="width: 120px" @change="loadCodes">
          <el-option label="有效" :value="1" />
          <el-option label="作废" :value="0" />
        </el-select>
        <el-input v-model="codeQuery.batchNo" placeholder="批次号" clearable style="width: 180px" @keyup.enter="loadCodes" />
        <el-button type="primary" icon="Search" @click="loadCodes">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>

      <div class="batch-bar">
        <span class="selected-tip">已选 {{ selectedRows.length }} 项</span>
        <el-button size="small" :disabled="!selectedRows.length" type="warning" @click="batchStatus(0)">批量作废</el-button>
        <el-button size="small" :disabled="!selectedRows.length" type="success" @click="batchStatus(1)">批量恢复</el-button>
        <el-popconfirm title="确定删除选中的二维码？删除后不可恢复" @confirm="batchDelete">
          <template #reference>
            <el-button size="small" :disabled="!selectedRows.length" type="danger">批量删除</el-button>
          </template>
        </el-popconfirm>
        <el-button size="small" :disabled="!selectedRows.length" type="primary" plain icon="Printer" @click="printLabels">打印标签</el-button>
      </div>

      <el-table :data="codes" v-loading="codesLoading" border :row-class-name="codeRowClassName" @selection-change="rows => (selectedRows = rows)">
        <el-table-column type="selection" width="45" fixed="left" />
        <el-table-column label="二维码" width="110" fixed="left">
          <template #default="{ row }">
            <div v-if="row.qrImageUrl" class="qr-cell">
              <el-image :src="resolveMediaUrl(row.qrImageUrl)" referrerpolicy="no-referrer" style="width: 70px; height: 70px; background: #f5f7fa; cursor: zoom-in" fit="contain" @click="openQrPreview(row.qrImageUrl)">
                <template #error><div class="qr-img-err">加载失败</div></template>
              </el-image>
              <div class="qr-row-btns">
                <el-button link type="primary" size="small" @click="downloadQr(row)">下载</el-button>
              </div>
            </div>
            <div v-else class="qr-cell">
              <div class="qr-placeholder">暂无</div>
              <el-button link type="success" size="small" :loading="qrGenerating[row.id]" @click="generateQr(row)">生成</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="code" label="码值" width="140" />
        <el-table-column prop="productNo" label="产品编号" width="130" />
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="spec" label="规格" width="110" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row.codeType === 1 ? 'primary' : 'warning'" size="small">{{ row.codeType === 1 ? '普通码' : '一物一码' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次号" width="160" />
        <el-table-column label="扫码次数" width="110">
          <template #default="{ row }">{{ row.scanCount }} / {{ row.scanLimit === 0 ? '不限' : row.scanLimit }}</template>
        </el-table-column>
        <el-table-column label="违规扫码" width="130">
          <template #default="{ row }">
            <div v-if="row.voidScanCount > 0 || row.overLimitScanCount > 0" class="violation-cell">
              <div v-if="row.voidScanCount > 0" class="violation-item void">作废码被扫 <b>{{ row.voidScanCount }}</b> 次</div>
              <div v-if="row.overLimitScanCount > 0" class="violation-item over">超限续扫 <b>{{ row.overLimitScanCount }}</b> 次</div>
            </div>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '有效' : '作废' }}</el-tag>
            <el-tag v-if="row.scanLimit > 0 && row.scanCount > row.scanLimit" type="danger" size="small" style="margin-left: 4px">预警</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="生成时间" width="170" />
        <el-table-column prop="lastScanTime" label="最近扫码" width="170">
          <template #default="{ row }">{{ row.lastScanTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '作废' : '恢复' }}
            </el-button>
            <el-popconfirm :title="`确定删除码值 ${row.code}？`" @confirm="remove(row)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="codeQuery.current"
        :total="codeTotal"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadCodes"
      />
    </el-card>
  </div>

  <!-- 二维码大图预览遮罩：点击空白/Esc/滚动关闭，滚轮/双指缩放 -->
  <Teleport to="body">
    <div v-if="qrPreview.visible" class="qr-mask" @click.self="closeQrPreview" @wheel.prevent="onQrWheel" @touchmove.self="closeQrPreview">
      <div class="qr-mask__box" :style="{ transform: `scale(${qrPreview.scale})` }" @click.stop>
        <img :src="qrPreview.url" class="qr-mask__img" alt="二维码大图" referrerpolicy="no-referrer" @click.stop @error="onImgError" />
      </div>
      <div class="qr-mask__tip">滚轮缩放 · 点击空白或按 Esc 关闭</div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../request'
import { resolveMediaUrl, onImgError } from '../media'

// ===== 二维码大图预览遮罩 =====
const qrPreview = reactive({ visible: false, url: '', scale: 1 })
function openQrPreview(url) {
  qrPreview.url = resolveMediaUrl(url)
  qrPreview.scale = 1
  qrPreview.visible = true
  document.addEventListener('keydown', onQrKeydown)
}
function closeQrPreview() {
  qrPreview.visible = false
  qrPreview.scale = 1
  document.removeEventListener('keydown', onQrKeydown)
}
function onQrKeydown(e) {
  if (e.key === 'Escape') closeQrPreview()
}
function onQrWheel(e) {
  // 滚轮缩放：向上放大、向下缩小，范围 0.5x ~ 4x
  const delta = e.deltaY < 0 ? 0.2 : -0.2
  qrPreview.scale = Math.min(4, Math.max(0.5, +(qrPreview.scale + delta).toFixed(2)))
}
onBeforeUnmount(() => document.removeEventListener('keydown', onQrKeydown))

const router = useRouter()

const products = ref([])
const productSearchLoading = ref(false)
const selectedProduct = ref(null)
const normalLoading = ref(false)
const normalResult = ref(null)
const normalCode = computed(() => normalResult.value?.code)
const batchCount = ref(100)
const batchScanLimit = ref(3)
const batchLoading = ref(false)
const batchTask = ref(null)
let pollTimer = null

const historyBatches = ref([])
const historyLoading = ref(false)

const codes = ref([])
const codesLoading = ref(false)
const qrGenerating = reactive({})
const codeTotal = ref(0)
const selectedRows = ref([])
const codeQuery = reactive({ current: 1, size: 10, codeType: null, status: null, batchNo: '', keyword: '' })

const batchPercent = computed(() => {
  if (!batchTask.value || !batchTask.value.total) return 0
  return Math.round((batchTask.value.generated / batchTask.value.total) * 100)
})

// 产品远程搜索：输入关键字查产品列表
async function searchProducts(keyword) {
  if (!keyword) {
    // 空关键字时加载前 20 条
    const res = await request.get('/admin/product/page', { params: { current: 1, size: 20 } })
    products.value = res.data.records
    return
  }
  productSearchLoading.value = true
  try {
    const res = await request.get('/admin/product/page', { params: { current: 1, size: 50, keyword } })
    products.value = res.data.records
  } finally {
    productSearchLoading.value = false
  }
}

function onProductChange() {
  loadCodes()
}

async function genNormal() {
  normalLoading.value = true
  try {
    const res = await request.post(`/admin/code/normal/${selectedProduct.value}`)
    normalResult.value = res.data
    ElMessage.success('普通产品码生成成功')
    loadCodes()
  } finally {
    normalLoading.value = false
  }
}

async function genBatch() {
  batchLoading.value = true
  try {
    const payload = { productId: selectedProduct.value, count: batchCount.value, batchNo: '' }
    // scanLimit 为 0 表示不限，但后端默认值是 null → 3，前端也传 null 让后端处理
    if (batchScanLimit.value !== null && batchScanLimit.value !== undefined && batchScanLimit.value !== '') {
      payload.scanLimit = batchScanLimit.value
    }
    const res = await request.post('/admin/code/batch', payload)
    batchTask.value = res.data
    ElMessage.success(`任务已启动，共 ${res.data.total} 个`)
    pollProgress(res.data.taskId)
    loadHistory()
  } finally {
    batchLoading.value = false
  }
}

function pollProgress(taskId) {
  clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    const res = await request.get(`/admin/code/batch/${taskId}`)
    batchTask.value = res.data
    if (res.data.status === 'SUCCESS' || res.data.status === 'FAIL') {
      clearInterval(pollTimer)
      if (res.data.status === 'SUCCESS') {
        ElMessage.success('批量生成完成')
        loadCodes()
      } else {
        ElMessage.error('生成失败：' + (res.data.message || ''))
      }
    }
  }, 1000)
}

async function downloadCsv() {
  const res = await request.get(`/admin/code/batch/${batchTask.value.taskId}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(new Blob([res.data]))
  const a = document.createElement('a')
  a.href = url
  a.download = `${batchTask.value.batchNo}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadQr(row) {
  if (!row.qrImageUrl) {
    ElMessage.warning('该码未生成二维码图片')
    return
  }
  try {
    const resp = await fetch(resolveMediaUrl(row.qrImageUrl))
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.productNo || 'code'}_${row.code}.png`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    window.open(row.qrImageUrl, '_blank')
  }
}

// ========== 历史批次 & 违规行高亮 ==========

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await request.get('/admin/code/batch/list', { params: { current: 1, size: 50 } })
    historyBatches.value = res.data.records || []
  } finally {
    historyLoading.value = false
  }
}

async function downloadHistoryCsv(row) {
  try {
    const res = await request.get(`/admin/code/batch/${row.taskId}/download`, { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.batchNo}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('下载失败，文件可能已过期')
  }
}

function codeRowClassName({ row }) {
  // 严重红底：作废状态下有违规扫码，或超限续扫
  if ((row.status === 0 && row.voidScanCount > 0) || (row.overLimitScanCount > 0)) {
    return 'row-danger'
  }
  // 轻微黄底：已作废但暂无违规
  if (row.status === 0) {
    return 'row-warn'
  }
  // 已超限但还没续扫过
  if (row.scanLimit > 0 && row.scanCount > row.scanLimit && row.overLimitScanCount === 0) {
    return 'row-warn'
  }
  return ''
}

function batchStatusTag(status) {
  switch (status) {
    case 'RUNNING': return ''
    case 'SUCCESS': return 'success'
    case 'FAILED': return 'danger'
    default: return 'info'
  }
}

async function generateQr(row) {
  qrGenerating[row.id] = true
  try {
    const res = await request.post(`/admin/code/qr/${row.id}`)
    row.qrImageUrl = res.data.qrImageUrl
    ElMessage.success('二维码生成成功')
  } finally {
    qrGenerating[row.id] = false
  }
}

async function loadCodes() {
  codesLoading.value = true
  try {
    const params = { ...codeQuery, productId: selectedProduct.value }
    const res = await request.get('/admin/code/page', { params })
    codes.value = res.data.records
    codeTotal.value = Number(res.data.total)
  } finally {
    codesLoading.value = false
  }
}

function resetQuery() {
  codeQuery.codeType = null
  codeQuery.status = null
  codeQuery.batchNo = ''
  codeQuery.keyword = ''
  codeQuery.current = 1
  loadCodes()
}

async function toggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  await request.post(`/admin/code/${row.id}/status`, null, { params: { status: target } })
  row.status = target
  ElMessage.success(target === 1 ? '已恢复为有效' : '已作废，扫码将提示失效')
}

async function remove(row) {
  await request.delete(`/admin/code/${row.id}`)
  ElMessage.success('已删除')
  loadCodes()
}

async function batchStatus(status) {
  const ids = selectedRows.value.map(r => r.id)
  if (!ids.length) return
  const title = status === 1 ? '恢复' : '作废'
  try {
    await ElMessageBox.confirm(`确定${title}选中的 ${ids.length} 个二维码？`, '批量操作', { type: 'warning' })
  } catch {
    return
  }
  const res = await request.post('/admin/code/batch/status', { ids, status })
  ElMessage.success(`已${title} ${res.data.count} 个二维码`)
  loadCodes()
}

async function batchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  if (!ids.length) return
  const res = await request.post('/admin/code/batch/delete', { ids })
  ElMessage.success(`已删除 ${res.data.count} 个二维码`)
  loadCodes()
}

// 跳标签打印页（新标签页打开，保留当前列表筛选状态）
function printLabels() {
  const ids = selectedRows.value.map(r => r.id)
  if (!ids.length) return
  const { href } = router.resolve({ path: '/code-print', query: { ids: ids.join(',') } })
  window.open(href, '_blank')
}

onMounted(() => {
  searchProducts('')
  loadCodes()
  loadHistory()
})
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.batch-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.selected-tip { font-size: 13px; color: #909399; margin-right: 4px; }
.normal-result { display: flex; gap: 16px; align-items: center; }
.batch-progress { display: flex; align-items: center; gap: 16px; }
.qr-cell { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.qr-placeholder { width: 70px; height: 70px; border: 1px dashed #dcdfe6; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 12px; border-radius: 4px; }

.form-hint { font-size: 12px; color: #909399; margin-left: 8px; }
.muted { color: #c0c4cc; font-size: 12px; }

/* 违规行高亮 */
:deep(.el-table .row-danger) { background-color: #fef0f0 !important; }
:deep(.el-table .row-danger td) { color: #c0392b !important; font-weight: 500; }
:deep(.el-table .row-warn) { background-color: #fdf6ec !important; }
:deep(.el-table .row-warn td) { color: #e6a23c !important; }

.violation-cell { font-size: 12px; line-height: 1.6; }
.violation-item.void { color: #e6a23c; }
.qr-img-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 11px; background: #f2f3f5; }

/* 二维码大图遮罩（非 scoped 也能命中，因 Teleport 到 body，用全局选择器） */
</style>
<style>
.qr-mask {
  position: fixed; inset: 0; z-index: 3000; background: rgba(0, 0, 0, 0.72);
  display: flex; align-items: center; justify-content: center; cursor: zoom-out;
  touch-action: none;
}
.qr-mask__box {
  background: #fff; padding: 28px; border-radius: 10px; cursor: default;
  transition: transform 0.15s ease; box-shadow: 0 12px 48px rgba(0, 0, 0, 0.4);
}
.qr-mask__img { display: block; width: min(60vw, 60vh); height: min(60vw, 60vh); object-fit: contain; }
.qr-mask__tip {
  position: absolute; bottom: 24px; left: 50%; transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.75); font-size: 13px; pointer-events: none;
}
.violation-item.over { color: #c0392b; }
.violation-item b { font-size: 14px; }
</style>
