<template>
  <div v-loading="loading">
    <!-- 核心指标 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="4"><el-card><div class="num">{{ data.productCount ?? '-' }}</div><div class="label">产品总数</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="num">{{ data.codeCount ?? '-' }}</div><div class="label">二维码总数</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="num blue">{{ data.todayScan ?? '-' }}</div><div class="label">今日扫码</div><div class="sub">昨日 {{ data.yesterdayScan ?? 0 }}</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="num">{{ data.totalScan ?? '-' }}</div><div class="label">累计扫码</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="num red">{{ data.fakeCount ?? '-' }}</div><div class="label">假货预警</div><div class="sub">今日 {{ data.todayFakeCount ?? 0 }}</div></el-card></el-col>
      <el-col :span="4"><el-card><div class="num">{{ data.viewCount ?? '-' }}</div><div class="label">累计浏览</div></el-card></el-col>
    </el-row>

    <!-- 产品 / 二维码 细分 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card>
          <template #header>产品状态</template>
          <div class="stat-grid">
            <div class="stat"><div class="v green">{{ data.onlineCount ?? '-' }}</div><div class="k">已上架</div></div>
            <div class="stat"><div class="v">{{ data.offlineCount ?? '-' }}</div><div class="k">已下架</div></div>
            <div class="stat"><div class="v blue">{{ data.todayProductCount ?? '-' }}</div><div class="k">今日新增</div></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>二维码构成</template>
          <div class="stat-grid">
            <div class="stat"><div class="v">{{ data.normalCodeCount ?? '-' }}</div><div class="k">普通产品码</div></div>
            <div class="stat"><div class="v">{{ data.uniqueCodeCount ?? '-' }}</div><div class="k">一物一码</div></div>
            <div class="stat"><div class="v blue">{{ data.todayCodeCount ?? '-' }}</div><div class="k">今日新增</div></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>码使用状态</template>
          <div class="stat-grid">
            <div class="stat"><div class="v green">{{ data.activeCodeCount ?? '-' }}</div><div class="k">有效</div></div>
            <div class="stat"><div class="v red">{{ data.invalidCodeCount ?? '-' }}</div><div class="k">已作废</div></div>
            <div class="stat"><div class="v">{{ data.scannedCodeCount ?? '-' }}</div><div class="k">已扫码</div></div>
            <div class="stat"><div class="v">{{ data.unscannedCodeCount ?? '-' }}</div><div class="k">未扫码</div></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-head">
              <span>扫码趋势</span>
              <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
                <el-radio-button :value="7">近 7 日</el-radio-button>
                <el-radio-button :value="30">近 30 日</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div class="chart" :class="{ dense: trendDays === 30 }">
            <div v-for="item in trend" :key="item.date" class="bar-group">
              <div class="bars">
                <div class="bar" :style="{ height: barHeight(item.count) }" :title="`扫码 ${item.count}`"></div>
                <div class="bar fake" :style="{ height: barHeight(item.fakeCount) }" :title="`预警 ${item.fakeCount}`"></div>
              </div>
              <div class="date">{{ trendDays === 30 ? item.date.slice(5).replace('-', '/') : item.date.slice(5) }}</div>
            </div>
            <div v-if="!trend.length" class="empty">暂无数据</div>
          </div>
          <div class="legend">
            <span class="dot"></span>扫码量
            <span class="dot fake-dot"></span>假货预警
          </div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header>每日新增二维码（近 7 日）</template>
          <div class="chart">
            <div v-for="item in detail.codeTrend" :key="item.name" class="bar-group">
              <div class="bars">
                <div class="bar green-bar" :style="{ height: barHeight(item.cnt, codeMax) }" :title="`新增 ${item.cnt}`"></div>
              </div>
              <div class="date">{{ item.name.slice(5) }}</div>
            </div>
          </div>
          <div class="legend"><span class="dot green-dot"></span>新增码数量</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 分布 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="8">
        <el-card>
          <template #header>产品分类分布</template>
          <div v-if="detail.categoryStats?.length" class="dist">
            <div v-for="item in detail.categoryStats" :key="item.name" class="dist-row">
              <span class="dist-name" :title="item.name">{{ item.name }}</span>
              <div class="dist-bar"><i :style="{ width: pct(item.cnt, maxCategory) }"></i></div>
              <span class="dist-val">{{ item.cnt }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无分类数据</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>品牌分布</template>
          <div v-if="detail.brandStats?.length" class="dist">
            <div v-for="item in detail.brandStats" :key="item.name" class="dist-row">
              <span class="dist-name" :title="item.name">{{ item.name }}</span>
              <div class="dist-bar"><i :style="{ width: pct(item.cnt, maxBrand) }"></i></div>
              <span class="dist-val">{{ item.cnt }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无品牌数据</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>扫码来源分布</template>
          <div v-if="detail.sourceStats?.length" class="dist">
            <div v-for="item in detail.sourceStats" :key="item.name" class="dist-row">
              <span class="dist-name">{{ sourceLabel(item.name) }}</span>
              <div class="dist-bar"><i :style="{ width: pct(item.cnt, maxSource) }"></i></div>
              <span class="dist-val">{{ item.cnt }}</span>
            </div>
          </div>
          <div v-else class="empty">暂无扫码记录</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热门产品 + 最近扫码 -->
    <el-row :gutter="16" class="cards">
      <el-col :span="10">
        <el-card>
          <template #header>扫码热门产品 Top 5</template>
          <el-table :data="detail.topProducts || []" size="small" border>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="productNo" label="货号" width="120" />
            <el-table-column prop="productName" label="品名" min-width="120" show-overflow-tooltip />
            <el-table-column prop="scanCount" label="扫码次数" width="90" align="right" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-head">
              <span>最近扫码记录</span>
              <el-button link type="primary" @click="$router.push('/stats')">查看全部</el-button>
            </div>
          </template>
          <el-table :data="recentScans" size="small" border>
            <el-table-column prop="code" label="码值" width="140" show-overflow-tooltip />
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag :type="row.isFake === 1 ? 'danger' : 'success'" size="small">{{ row.isFake === 1 ? '假货预警' : '正常' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" width="100">
              <template #default="{ row }">{{ sourceLabel(row.scanSource) }}</template>
            </el-table-column>
            <el-table-column prop="scanIp" label="IP" width="130" show-overflow-tooltip />
            <el-table-column prop="scanTime" label="扫码时间" min-width="160" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../request'

const loading = ref(false)
const data = ref({})
const trend = ref([])
const trendDays = ref(7)
const detail = ref({})
const recentScans = ref([])

const maxCount = computed(() => Math.max(1, ...trend.value.map(i => Number(i.count))))
const codeMax = computed(() => Math.max(1, ...(detail.value.codeTrend || []).map(i => Number(i.cnt))))
const maxCategory = computed(() => Math.max(1, ...(detail.value.categoryStats || []).map(i => Number(i.cnt))))
const maxBrand = computed(() => Math.max(1, ...(detail.value.brandStats || []).map(i => Number(i.cnt))))
const maxSource = computed(() => Math.max(1, ...(detail.value.sourceStats || []).map(i => Number(i.cnt))))

function barHeight(v, max) {
  const m = max ?? maxCount.value
  return Math.max(2, Math.round((Number(v) / m) * 140)) + 'px'
}

function pct(v, max) {
  return Math.max(4, Math.round((Number(v) / max) * 100)) + '%'
}

function sourceLabel(source) {
  const map = { '0': '未知', '1': '微信小程序', '2': 'H5' }
  return map[String(source)] || '未知'
}

async function loadTrend() {
  const res = await request.get('/admin/stats/trend', { params: { days: trendDays.value } })
  trend.value = res.data
}

onMounted(async () => {
  loading.value = true
  try {
    const [o, t, d, s] = await Promise.all([
      request.get('/admin/stats/overview'),
      request.get('/admin/stats/trend', { params: { days: trendDays.value } }),
      request.get('/admin/stats/detail'),
      request.get('/admin/stats/scan-page', { params: { current: 1, size: 8 } })
    ])
    data.value = o.data
    trend.value = t.data
    detail.value = d.data
    recentScans.value = s.data.records
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.cards { margin-bottom: 16px; }
.cards .el-card { text-align: center; }
.num { font-size: 28px; font-weight: bold; color: #303133; }
.num.blue { color: #409eff; }
.num.red { color: #f56c6c; }
.label { color: #909399; margin-top: 8px; }
.sub { color: #c0c4cc; font-size: 12px; margin-top: 4px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.stat-grid { display: flex; justify-content: space-around; }
.stat { text-align: center; padding: 4px 8px; }
.stat .v { font-size: 22px; font-weight: bold; color: #303133; }
.stat .v.green { color: #67c23a; }
.stat .v.blue { color: #409eff; }
.stat .v.red { color: #f56c6c; }
.stat .k { font-size: 12px; color: #909399; margin-top: 6px; }
.chart { display: flex; align-items: flex-end; gap: 16px; height: 180px; padding: 10px 12px 0; overflow-x: auto; }
.chart.dense { gap: 6px; }
.bar-group { display: flex; flex-direction: column; align-items: center; flex: 1; min-width: 18px; }
.bars { display: flex; align-items: flex-end; gap: 3px; height: 140px; }
.bar { width: 18px; background: #409eff; border-radius: 3px 3px 0 0; }
.chart.dense .bar { width: 8px; }
.bar.fake { background: #f56c6c; }
.bar.green-bar { background: #67c23a; }
.date { margin-top: 6px; font-size: 12px; color: #909399; white-space: nowrap; }
.legend { margin-top: 10px; font-size: 12px; color: #606266; }
.dot { display: inline-block; width: 10px; height: 10px; background: #409eff; border-radius: 2px; margin: 0 6px 0 16px; }
.fake-dot { background: #f56c6c; }
.green-dot { background: #67c23a; }
.dist-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.dist-name { width: 80px; font-size: 13px; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dist-bar { flex: 1; height: 10px; background: #f0f2f5; border-radius: 5px; overflow: hidden; }
.dist-bar i { display: block; height: 100%; background: #409eff; border-radius: 5px; }
.dist-val { width: 40px; text-align: right; font-size: 13px; color: #303133; }
.empty { text-align: center; color: #c0c4cc; font-size: 13px; padding: 24px 0; }
</style>
