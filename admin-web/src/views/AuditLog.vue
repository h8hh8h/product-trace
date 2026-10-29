<template>
  <div class="audit-page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="模块">
          <el-select v-model="query.module" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="m in options.modules" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="query.action" placeholder="全部" clearable style="width: 160px">
            <el-option v-for="a in options.actions" :key="a" :label="a" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="query.operator" placeholder="用户名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="query.success" placeholder="全部" clearable style="width: 110px">
            <el-option label="成功" :value="1" />
            <el-option label="失败" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="createdAt" label="操作时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="adminUsername" label="操作人" width="120">
          <template #default="{ row }">{{ row.adminUsername || '-' }}</template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="action" label="动作" width="130" />
        <el-table-column prop="targetId" label="对象ID" width="110">
          <template #default="{ row }">{{ row.targetId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="costMs" label="耗时" width="90">
          <template #default="{ row }">{{ row.costMs }} ms</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="row.responseCode === 200 ? 'success' : 'danger'" size="small">
              {{ row.responseCode === 200 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="search"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="操作详情" width="720px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.adminUsername || '-' }}</el-descriptions-item>
        <el-descriptions-item label="模块">{{ detail.module }}</el-descriptions-item>
        <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="请求方式">{{ detail.httpMethod }}</el-descriptions-item>
        <el-descriptions-item label="请求路径">{{ detail.requestUri }}</el-descriptions-item>
        <el-descriptions-item label="对象ID">{{ detail.targetId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端 IP">{{ detail.ip }}</el-descriptions-item>
        <el-descriptions-item label="耗时">{{ detail.costMs }} ms</el-descriptions-item>
        <el-descriptions-item label="返回码">{{ detail.responseCode }}</el-descriptions-item>
        <el-descriptions-item label="方法" :span="2">{{ detail.method }}</el-descriptions-item>
        <el-descriptions-item label="请求参数" :span="2">
          <pre class="params">{{ detail.requestParams || '无' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item v-if="detail.errorMsg" label="错误信息" :span="2">
          <span class="error-text">{{ detail.errorMsg }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import request from '../request'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const options = reactive({ modules: [], actions: [] })
const dateRange = ref([])
const detailVisible = ref(false)
const detail = ref({})

const query = reactive({
  current: 1,
  size: 20,
  module: '',
  action: '',
  operator: '',
  success: null
})

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    const params = { current: query.current, size: query.size }
    if (query.module) params.module = query.module
    if (query.action) params.action = query.action
    if (query.operator) params.operator = query.operator
    if (query.success !== null && query.success !== '') params.success = query.success
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const res = await request.get('/admin/audit-log/page', { params })
    rows.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.current = 1
  load()
}

function reset() {
  query.module = ''
  query.action = ''
  query.operator = ''
  query.success = null
  dateRange.value = []
  search()
}

function showDetail(row) {
  detail.value = row
  detailVisible.value = true
}

async function loadOptions() {
  try {
    const res = await request.get('/admin/audit-log/options')
    options.modules = res.data.modules || []
    options.actions = res.data.actions || []
  } catch (e) {
    /* 下拉项失败不影响主表格 */
  }
}

onMounted(() => {
  load()
  loadOptions()
})
</script>

<style scoped>
.audit-page { display: flex; flex-direction: column; gap: 12px; }
.filter-form :deep(.el-form-item) { margin-bottom: 8px; }
.table-card :deep(.el-card__body) { padding: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
.params {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: Consolas, monospace;
  font-size: 12px;
  color: #303133;
}
.error-text { color: #f56c6c; }
</style>
