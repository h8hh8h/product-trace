<template>
  <div class="scan-stats">
    <el-card shadow="never" class="geo-card">
      <template #header>
        <div class="card-head">
          <span>扫码地域分布</span>
          <span class="hint">
            已解析 {{ geo.resolved }} / {{ geo.total }} 条
            <template v-if="geo.unresolved > 0">（{{ geo.unresolved }} 条内网或无法识别）</template>
          </span>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="12">
          <div class="sub-title">省份排行</div>
          <div v-if="geo.provinces.length === 0" class="empty">暂无数据</div>
          <div v-for="item in geo.provinces" :key="item.name" class="bar-row">
            <span class="bar-name">{{ item.name }}</span>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(item.cnt) }"></div>
            </div>
            <span class="bar-cnt">{{ item.cnt }}</span>
          </div>
        </el-col>
        <el-col :span="12">
          <div class="sub-title">城市 TOP 10</div>
          <el-table :data="geo.cities" size="small" border max-height="320">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="城市" />
            <el-table-column prop="cnt" label="扫码次数" width="110" />
          </el-table>
        </el-col>
      </el-row>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input v-model="query.code" placeholder="码值" clearable style="width: 180px" @keyup.enter="load" />
        <el-select v-model="query.province" placeholder="省份" clearable filterable style="width: 150px" @change="load">
          <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
        </el-select>
        <el-select v-model="query.isFake" placeholder="结果" clearable style="width: 140px" @change="load">
          <el-option label="正常" :value="0" />
          <el-option label="假货预警" :value="1" />
        </el-select>
        <el-date-picker v-model="query.date" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" @change="load" />
        <el-button type="primary" icon="Search" @click="load">查询</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="码值" width="150" />
        <el-table-column prop="productId" label="产品ID" width="90" />
        <el-table-column prop="scanIp" label="扫码 IP" width="140" />
        <el-table-column label="归属地" width="180">
          <template #default="{ row }">
            <span v-if="row.scanProvince || row.scanCity">
              {{ [row.scanProvince, row.scanCity].filter(Boolean).join(' ') }}
            </span>
            <span v-else class="muted">未知</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            {{ ['未知', '微信小程序', 'H5'][row.scanSource] || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.isFake === 1" type="danger">假货预警</el-tag>
            <el-tag v-else-if="row.scanResult === 3" type="info">无效码</el-tag>
            <el-tag v-else-if="row.scanResult === 4" type="warning">作废码</el-tag>
            <el-tag v-else type="success">正常</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scanTime" label="扫码时间" width="180" />
        <el-table-column prop="userAgent" label="User-Agent" min-width="200" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import request from '../request'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, code: '', isFake: null, province: '', date: null })

const geo = reactive({ total: 0, resolved: 0, unresolved: 0, provinces: [], cities: [] })

/** 省份下拉选项：来自地域分布接口，避免手写省市字典 */
const provinceOptions = computed(() =>
  geo.provinces.map((p) => p.name).filter((n) => n !== '未知')
)

/** 以最大省份计数为 100% 计算条形宽度 */
const maxProvinceCnt = computed(() =>
  geo.provinces.reduce((max, item) => Math.max(max, Number(item.cnt) || 0), 0)
)

function barWidth(cnt) {
  if (!maxProvinceCnt.value) return '0%'
  return `${Math.max(2, (Number(cnt) / maxProvinceCnt.value) * 100)}%`
}

async function loadGeo() {
  try {
    const res = await request.get('/admin/stats/geo-distribution')
    Object.assign(geo, res.data)
  } catch (e) {
    /* 地域分布失败不影响扫码明细 */
  }
}

async function load() {
  loading.value = true
  try {
    const res = await request.get('/admin/stats/scan-page', { params: query })
    list.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadGeo()
  load()
})
</script>

<style scoped>
.scan-stats { display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.hint { font-size: 12px; color: #909399; }
.sub-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 10px; }
.empty { color: #c0c4cc; font-size: 13px; padding: 20px 0; text-align: center; }
.bar-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; font-size: 12px; }
.bar-name { width: 96px; flex: none; color: #606266; text-align: right; }
.bar-track { flex: 1; height: 14px; background: #f2f6fc; border-radius: 7px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 7px; background: linear-gradient(90deg, #79bbff, #409eff); }
.bar-cnt { width: 56px; flex: none; color: #303133; text-align: right; }
.muted { color: #c0c4cc; }
</style>
