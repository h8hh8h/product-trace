<template>
  <el-card>
    <template #header>
      <div class="card-head">
        <span>站点设置（小程序首页）</span>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </div>
    </template>

    <el-form label-width="120px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="站点名称">
            <el-input v-model="form.site_name" placeholder="顶部/分享时展示的站点名" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="首页启用">
            <el-switch v-model="homeEnabled" active-value="1" inactive-value="0" />
            <span class="tip">关闭后扫码直接进详情页，不再出现首页</span>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="主标题">
            <el-input v-model="form.home_title" placeholder="例如「产品溯源 · 品质保证」" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="副标题">
            <el-input v-model="form.home_subtitle" placeholder="首页顶部副标题，留空不显示" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Banner 图">
            <div class="banner-actions">
              <el-upload :show-file-list="false" accept="image/*" :http-request="uploadBanner">
                <el-button icon="Upload" :loading="uploadingBanner">上传图片</el-button>
              </el-upload>
              <el-button icon="FolderOpened" @click="pickerVisible = true">从媒体库选择（可多选）</el-button>
              <el-input v-model="bannerUrlInput" placeholder="或粘贴网络图片地址（https:// 开头）后点添加" style="width: 340px; margin-left: 8px" @keyup.enter="addBannerUrl">
                <template #append><el-button @click="addBannerUrl">添加</el-button></template>
              </el-input>
            </div>
            <div class="tip-block">
              建议尺寸 1920x600（或 750x300，比例 16:5）；JPG/PNG/WebP，≤2MB 超限自动前端压缩；支持多张，H5 首页自动轮播。
            </div>
            <div class="banner-list">
              <div v-for="(img, i) in banners" :key="i" class="banner-item">
                <el-image :src="resolveMediaUrl(img)" referrerpolicy="no-referrer" fit="contain" class="banner-img" :preview-src-list="[resolveMediaUrl(img)]" preview-teleported>
                  <template #error><div class="img-err">加载失败，请检查链接或防盗链</div></template>
                </el-image>
                <el-icon class="banner-del" @click="banners.splice(i, 1)"><Close /></el-icon>
              </div>
              <div v-if="!banners.length" class="banner-empty">暂未设置 Banner</div>
            </div>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="推荐产品">
            <el-select
              v-model="recommendIds"
              multiple
              filterable
              placeholder="从产品列表里挑选推荐在首页展示的产品"
              style="width: 100%"
            >
              <el-option v-for="p in productOptions" :key="p.id" :label="`${p.productNo} · ${p.productName}`" :value="p.id" />
            </el-select>
            <div class="tip">未配置时自动取最新上架的前 6 个产品</div>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </el-card>

  <!-- 媒体库选择（Banner 多选） -->
  <MediaPicker v-model="pickerVisible" type="image" :multiple="true" :max-count="9" @select="onBannerPickerSelect" />
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../request'
import { resolveMediaUrl } from '../media'
import Compressor from 'compressorjs'
import MediaPicker from '../components/MediaPicker.vue'

const form = reactive({})
const banners = ref([])
const bannerUrlInput = ref('')
const recommendIds = ref([])
const productOptions = ref([])
const saving = ref(false)
const homeEnabled = ref('1')

// ===== Banner 压缩 + 媒体库 =====
const IMG_LIMIT = 2 * 1024 * 1024
const pickerVisible = ref(false)
const uploadingBanner = ref(false)

function compressImage(file) {
  return new Promise((resolve) => {
    if (file.size <= IMG_LIMIT) return resolve(file)
    const quality = file.size > 4 * 1024 * 1024 ? 0.55 : 0.7
    new Compressor(file, {
      quality, maxWidth: 1920, maxHeight: 1920, mimeType: 'image/jpeg',
      success(r) { resolve(new File([r], file.name.replace(/\.\w+$/, '.jpg'), { type: 'image/jpeg' })) },
      error() { resolve(file) }
    })
  })
}

/** Banner 上传命名：banner_时间戳.ext */
function bannerStampName(file) {
  const ext = (String(file?.name || '').split('.').pop() || 'jpg').toLowerCase()
  return `banner_${Date.now()}.${ext}`
}

function onBannerPickerSelect(items) {
  const urls = (items || []).map(i => i.url).filter(Boolean)
  urls.forEach(u => { if (!banners.value.includes(u)) banners.value.push(u) })
  if (urls.length) ElMessage.success(`已添加 ${urls.length} 张 Banner`)
}

async function load() {
  const r = await request.get('/admin/site-config')
  for (const c of r.data) {
    form[c.configKey] = c.configValue
  }
  banners.value = parseJson(form.home_banner)
  recommendIds.value = parseJson(form.home_recommend).map(Number)
  homeEnabled.value = form.home_enabled || '1'

  const p = await request.get('/admin/product/page', { params: { current: 1, size: 500, keyword: '' } })
  productOptions.value = p.data.records.filter(x => x.shelfStatus === 1)
}

function parseJson(raw) {
  if (!raw) return []
  try { return JSON.parse(raw) || [] } catch { return [] }
}

async function uploadBanner(option) {
  uploadingBanner.value = true
  try {
    const f = await compressImage(option.file)
    const fd = new FormData()
    fd.append('file', f, bannerStampName(f))
    const r = await request.post('/admin/media/image', fd)
    banners.value.push(r.data.fileUrl)
    ElMessage.success('已添加 Banner')
  } catch (e) {
    ElMessage.error('上传失败：' + (e?.response?.data?.message || e?.message || '请重试'))
  } finally {
    uploadingBanner.value = false
  }
}

function addBannerUrl() {
  const url = bannerUrlInput.value.trim()
  if (!url) return
  if (!/^https?:\/\//i.test(url)) {
    ElMessage.warning('请输入 http/https 地址')
    return
  }
  banners.value.push(url)
  bannerUrlInput.value = ''
}

async function save() {
  saving.value = true
  try {
    const body = { ...form }
    body.home_banner = JSON.stringify(banners.value.map(u => String(u).trim()).filter(Boolean))
    body.home_recommend = JSON.stringify(recommendIds.value)
    body.home_enabled = homeEnabled.value
    await request.post('/admin/site-config/save', body)
    ElMessage.success('保存成功')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.tip { margin-left: 10px; color: #909399; font-size: 12px; }
.banner-actions { display: flex; align-items: center; margin-bottom: 6px; }
.tip-block { color: #909399; font-size: 12px; line-height: 1.6; margin-bottom: 10px; }
.banner-list { display: flex; gap: 10px; flex-wrap: wrap; }
.banner-item { position: relative; }
.banner-img { width: 260px; height: 120px; border-radius: 6px; background: #f5f7fa; }
.img-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 12px; background: #f2f3f5; text-align: center; padding: 8px; }
.banner-del {
  position: absolute; top: -6px; right: -6px; background: #f56c6c; color: #fff;
  border-radius: 50%; width: 20px; height: 20px; display: flex; align-items: center; justify-content: center;
  cursor: pointer;
}
.banner-empty { color: #c0c4cc; font-size: 12px; }
</style>
