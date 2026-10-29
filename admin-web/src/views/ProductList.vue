<template>
  <div>
    <el-card>
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="产品名称 / 编号" clearable style="width: 220px" @clear="load" @keyup.enter="load" />
        <el-select v-model="query.shelfStatus" placeholder="状态" clearable style="width: 120px" @change="load">
          <el-option label="已上架" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button type="primary" icon="Search" @click="load">查询</el-button>
        <el-button type="success" icon="Plus" @click="openEdit()">新建产品</el-button>
        <el-button type="warning" plain icon="Upload" @click="openImport">批量导入</el-button>
        <el-button plain @click="openDict('brand')">品牌维护</el-button>
        <el-button plain @click="openDict('category')">分类维护</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <el-image v-if="row.coverImage" :src="resolveMediaUrl(row.coverImage)" referrerpolicy="no-referrer" style="width: 60px; height: 60px; background: #f5f7fa" fit="contain" :preview-src-list="[resolveMediaUrl(row.coverImage)]" preview-teleported>
              <template #error><div class="img-err">加载失败</div></template>
            </el-image>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="productNo" label="货号" width="120" />
        <el-table-column prop="productName" label="品名" min-width="160" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="thickness" label="厚度" width="90" />
        <el-table-column prop="color" label="颜色" width="90" />
        <el-table-column prop="size" label="尺码" width="90" />
        <el-table-column prop="spec" label="规格" width="110" />
        <el-table-column prop="crotchType" label="裆型" width="90" />
        <el-table-column prop="material" label="材质" width="110" />
        <el-table-column prop="composition" label="成分" min-width="140" />
        <el-table-column prop="retailPrice" label="统一零售价" width="120">
          <template #default="{ row }">{{ row.retailPrice != null ? '¥' + Number(row.retailPrice).toFixed(2) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="scanCount" label="浏览量" width="80">
          <template #default="{ row }">{{ row.viewCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.shelfStatus === 1 ? 'success' : 'info'">{{ row.shelfStatus === 1 ? '已上架' : '已下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="270" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="openView(row)">查看</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.shelfStatus === 1 ? 'warning' : 'success'" @click="toggleShelf(row)">
              {{ row.shelfStatus === 1 ? '下架' : '上架' }}
            </el-button>
            <el-popconfirm title="确定删除该产品？" @confirm="remove(row)">
              <template #reference><el-button size="small" type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.current"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="load"
      />
    </el-card>

    <el-dialog v-model="editVisible" :title="form.id ? '编辑产品' : '新建产品'" width="860px" top="4vh" destroy-on-close>
      <el-form :model="form" label-width="90px" @submit.prevent>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="货号" required><el-input v-model="form.productNo" placeholder="产品唯一货号" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="品名" required><el-input v-model="form.productName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="分类">
              <el-select v-model="form.category" filterable clearable placeholder="请先维护分类" style="width: 100%">
                <el-option v-for="c in categoryOptions" :key="c.id" :label="c.name" :value="c.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌">
              <el-select v-model="form.brand" filterable clearable placeholder="请先维护品牌" style="width: 100%">
                <el-option v-for="b in brandOptions" :key="b.id" :label="b.name" :value="b.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="产地"><el-input v-model="form.origin" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="规格"><el-input v-model="form.spec" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="厚度"><el-input v-model="form.thickness" placeholder="如 0.3cm / 20D" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="颜色"><el-input v-model="form.color" placeholder="如 黑色 / 肤色" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="尺码"><el-input v-model="form.size" placeholder="如 均码 / M / 80-120kg" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="裆型"><el-input v-model="form.crotchType" placeholder="如 加裆 / 无痕 / 单层" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="材质"><el-input v-model="form.material" placeholder="如 氨纶包覆纱 / 棉" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="成分"><el-input v-model="form.composition" placeholder="如 锦纶 85% 氨纶 15%" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="零售价">
              <el-input-number v-model="form.retailPrice" :min="0" :precision="2" :step="10" style="width: 100%" placeholder="统一零售价（元）" />
            </el-form-item>
          </el-col>
          <el-col :span="24"><el-form-item label="特点"><el-input v-model="form.feature" type="textarea" :rows="2" placeholder="如 防勾丝、透气、加裆" /></el-form-item></el-col>
          <el-col :span="24">
            <el-form-item label="展示项">
              <el-checkbox-group v-model="displayFieldsModel">
                <el-checkbox v-for="f in FIELD_OPTIONS" :key="f.key" :value="f.key">{{ f.label }}</el-checkbox>
              </el-checkbox-group>
              <div class="field-tip">
                勾选的字段会在用户扫码后的产品详情页展示；未填写内容的字段自动隐藏；「排序 / 创建时间」默认不勾选、不展示。
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上架状态">
              <el-radio-group v-model="form.shelfStatus">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item></el-col>
        </el-row>

        <el-form-item label="封面图">
          <div class="media-row">
            <el-upload :show-file-list="false" accept="image/*" :http-request="(o) => uploadImage(o, 'cover')" :disabled="uploadState.active">
              <el-button icon="Upload" :loading="uploadState.active">上传封面</el-button>
            </el-upload>
            <el-button icon="FolderOpened" :disabled="uploadState.active" @click="openPicker('cover')">从媒体库选择</el-button>
            <el-input v-model="coverUrlInput" placeholder="或粘贴网络图片地址（https:// 开头）" clearable style="width: 300px" @keyup.enter="addCoverUrl">
              <template #append><el-button @click="addCoverUrl">添加</el-button></template>
            </el-input>
            <el-image v-if="form.coverImage" :src="resolveMediaUrl(form.coverImage)" referrerpolicy="no-referrer" style="width: 80px; height: 80px; background: #f5f7fa; flex: none" fit="contain" :preview-src-list="[resolveMediaUrl(form.coverImage)]" preview-teleported>
              <template #error><div class="img-err">加载失败</div></template>
            </el-image>
          </div>
          <div class="field-tip">
            建议尺寸 800*800（正方形）；JPG/PNG/WebP，≤2MB 超限自动前端压缩；上传自动按「货号_时间戳」命名。
          </div>
          <el-checkbox v-model="coverAsFirst" style="margin-top: 4px">将封面图作为第一张轮播图展示</el-checkbox>
        </el-form-item>

        <el-form-item label="轮播图">
          <div class="media-row">
            <el-upload :show-file-list="false" accept="image/*" :http-request="(o) => uploadImage(o, 'gallery')" :disabled="uploadState.active">
              <el-button icon="Plus" :loading="uploadState.active">添加图片</el-button>
            </el-upload>
            <el-button icon="FolderOpened" :disabled="uploadState.active" @click="openPicker('gallery')">从媒体库选择（可多选）</el-button>
            <el-input v-model="galleryUrlInput" placeholder="或粘贴网络图片地址（https:// 开头）" clearable style="width: 300px" @keyup.enter="addGalleryUrl">
              <template #append><el-button @click="addGalleryUrl">添加</el-button></template>
            </el-input>
          </div>
          <div class="field-tip">
            建议尺寸 800*800（正方形）；JPG/PNG/WebP，≤2MB 超限自动前端压缩；上传自动按「货号_时间戳」命名。
          </div>
          <div class="gallery">
            <div v-for="(img, i) in galleryList" :key="i" class="gallery-item">
              <el-image :src="resolveMediaUrl(img)" referrerpolicy="no-referrer" style="width: 80px; height: 80px; background: #f5f7fa" fit="contain" :preview-src-list="[resolveMediaUrl(img)]" preview-teleported>
                <template #error><div class="img-err">加载失败</div></template>
              </el-image>
              <el-icon class="del" @click="galleryList.splice(i, 1)"><Close /></el-icon>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="产品视频">
          <div class="media-row">
            <el-upload :show-file-list="false" accept="video/*" :http-request="uploadVideo" :before-upload="beforeVideoUpload" :disabled="uploadState.active">
              <el-button icon="VideoCamera" :loading="uploadState.active">上传视频</el-button>
            </el-upload>
            <el-button icon="FolderOpened" :disabled="uploadState.active" @click="openPicker('video')">从媒体库选择</el-button>
            <el-input v-model="form.videoUrl" placeholder="或粘贴视频直链，如 https://xxx.com/product.mp4" clearable style="width: 340px" />
          </div>
          <div class="field-tip">
            MP4 格式，1080P 以内，时长建议 30~60 秒，≤50MB（超 50MB 建议先用剪映等工具压缩，过大会导致扫码加载缓慢）；上传自动按「货号_时间戳」命名。
          </div>
          <video v-if="form.videoUrl" :src="resolveMediaUrl(form.videoUrl)" controls preload="metadata" class="video-preview" />
        </el-form-item>

        <!-- 上传/压缩进度（全局单任务） -->
        <el-form-item v-if="uploadState.active" label="处理进度">
          <div class="upload-progress">
            <el-progress :percentage="uploadState.percent" :status="uploadState.percent >= 100 ? 'success' : ''" :stroke-width="14" style="flex: 1" />
            <div class="upload-progress__text">{{ uploadState.text }}</div>
          </div>
        </el-form-item>

        <el-form-item label="图文详情">
          <div class="editor-extra">
            <el-button icon="FolderOpened" @click="openPicker('rich')">插入媒体库图片</el-button>
            <span class="field-tip" style="margin: 0">详情图建议宽 750~800px、高不限，JPG/PNG/WebP，≤2MB（超限自动压缩）</span>
          </div>
          <div style="border: 1px solid #ccc; width: 100%">
            <Toolbar style="border-bottom: 1px solid #ccc" :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" />
            <Editor style="height: 300px; overflow-y: hidden" v-model="form.description" :defaultConfig="editorConfig" mode="default" @onCreated="e => editorRef = e" />
          </div>
          <div class="field-tip">
            点工具栏图片按钮，可选「上传图片」（本地文件，自动按货号命名）或「网络图片」（填写 http/https 图片地址，自动绕过防盗链）。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 产品预览（只读） -->
    <el-drawer v-model="viewVisible" :title="`产品预览 · ${viewProduct.productName || ''}`" size="640px">
      <div v-if="viewProduct.id" class="preview">
        <el-image v-if="viewProduct.coverImage" :src="resolveMediaUrl(viewProduct.coverImage)" referrerpolicy="no-referrer" class="preview__cover" fit="contain" :preview-src-list="[resolveMediaUrl(viewProduct.coverImage)]" preview-teleported>
          <template #error><div class="img-err img-err--lg">图片加载失败，请检查链接有效性或是否被防盗链拦截</div></template>
        </el-image>
        <div class="preview__head">
          <span class="preview__name">{{ viewProduct.productName }}</span>
          <el-tag :type="viewProduct.shelfStatus === 1 ? 'success' : 'info'" size="small">
            {{ viewProduct.shelfStatus === 1 ? '已上架' : '已下架' }}
          </el-tag>
          <el-tag size="small" type="info">浏览量 {{ viewProduct.viewCount || 0 }}</el-tag>
        </div>

        <el-descriptions :column="2" border size="small" class="preview__desc-table">
          <el-descriptions-item label="货号">{{ viewProduct.productNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="品名">{{ viewProduct.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ viewProduct.brand || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ viewProduct.category || '-' }}</el-descriptions-item>
          <el-descriptions-item label="裆型">{{ viewProduct.crotchType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="厚度">{{ viewProduct.thickness || '-' }}</el-descriptions-item>
          <el-descriptions-item label="颜色">{{ viewProduct.color || '-' }}</el-descriptions-item>
          <el-descriptions-item label="尺码">{{ viewProduct.size || '-' }}</el-descriptions-item>
          <el-descriptions-item label="规格">{{ viewProduct.spec || '-' }}</el-descriptions-item>
          <el-descriptions-item label="材质">{{ viewProduct.material || '-' }}</el-descriptions-item>
          <el-descriptions-item label="成分">{{ viewProduct.composition || '-' }}</el-descriptions-item>
          <el-descriptions-item label="零售价">
            {{ viewProduct.retailPrice != null ? '¥' + Number(viewProduct.retailPrice).toFixed(2) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="产地">{{ viewProduct.origin || '-' }}</el-descriptions-item>
          <el-descriptions-item label="排序">{{ viewProduct.sortOrder ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="特点" :span="2">{{ viewProduct.feature || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ viewProduct.createTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="previewGallery.length" class="preview__block">
          <div class="preview__block-title">轮播图</div>
          <div class="preview__gallery">
            <el-image v-for="(img, i) in previewGallery" :key="i" :src="resolveMediaUrl(img)" referrerpolicy="no-referrer" fit="contain" class="preview__gallery-img" :preview-src-list="previewGallery.map(resolveMediaUrl)" :initial-index="i" preview-teleported>
              <template #error><div class="img-err">加载失败</div></template>
            </el-image>
          </div>
        </div>

        <div v-if="viewProduct.videoUrl" class="preview__block">
          <div class="preview__block-title">产品视频</div>
          <video :src="resolveMediaUrl(viewProduct.videoUrl)" controls preload="metadata" class="preview__video" />
        </div>

        <div class="preview__block">
          <div class="preview__block-title">图文详情</div>
          <div v-if="viewProduct.description" class="preview__rich" v-html="injectNoReferrer(viewProduct.description)"></div>
          <div v-else class="preview__empty">暂无图文详情</div>
        </div>
      </div>
    </el-drawer>

    <!-- 批量导入 -->
    <el-dialog v-model="importVisible" title="批量导入产品" width="640px" destroy-on-close>
      <div class="import-tips">
        <p>1. 先下载模板，按表头填写产品数据（货号、品名必填，其余选填）。</p>
        <p>2. 品牌 / 分类 必须先在「品牌维护 / 分类维护」中存在，否则该行导入失败。</p>
        <p>3. 仅支持 .xlsx 文件，大小不超过 5MB；货号在库内或文件内重复会整行失败。</p>
      </div>
      <div class="import-actions">
        <el-button type="primary" plain icon="Download" :loading="templateLoading" @click="downloadTemplate">下载模板</el-button>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".xlsx"
          :on-change="onImportFileChange"
          :on-remove="() => (importFile = null)"
          style="display: inline-block"
        >
          <el-button icon="FolderOpened">选择文件</el-button>
        </el-upload>
        <el-button type="success" icon="Upload" :disabled="!importFile" :loading="importing" @click="submitImport">开始导入</el-button>
      </div>
      <template v-if="importResult">
        <el-alert
          :type="importResult.failed > 0 ? 'warning' : 'success'"
          :closable="false"
          :title="`共 ${importResult.total} 行：成功 ${importResult.success} 行，失败 ${importResult.failed} 行`"
          style="margin: 12px 0"
        />
        <el-table v-if="importResult.errors && importResult.errors.length" :data="importResult.errors" size="small" border max-height="260">
          <el-table-column prop="row" label="Excel 行号" width="100" />
          <el-table-column prop="message" label="错误原因" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 品牌 / 分类维护 -->
    <el-dialog v-model="dictVisible" :title="dictType === 'brand' ? '品牌维护' : '分类维护'" width="440px">
      <div class="dict-add">
        <el-input v-model="dictInput" placeholder="输入名称后回车添加" @keyup.enter="addDict" />
        <el-button type="primary" @click="addDict">添加</el-button>
      </div>
      <el-table :data="dictItems" size="small" border max-height="360">
        <el-table-column prop="name" label="名称" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-popconfirm :title="`确定删除「${row.name}」？`" @confirm="removeDict(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="dictVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 媒体资产库选择弹窗（封面/轮播/视频/图文详情共用） -->
    <MediaPicker v-model="pickerVisible" :type="pickerType" :multiple="pickerMultiple" :max-count="9" @select="onPickerSelect" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { ElMessage } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import request from '../request'
import { normalizeMediaInput, resolveMediaUrl, injectNoReferrer } from '../media'
import Compressor from 'compressorjs'
import MediaPicker from '../components/MediaPicker.vue'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 10, keyword: '', shelfStatus: null })

const editVisible = ref(false)
const saving = ref(false)
const form = reactive({})
const galleryList = ref([])
const coverUrlInput = ref('')
const galleryUrlInput = ref('')
const videoUploading = ref(false)
const coverAsFirst = ref(true)
const editorRef = shallowRef()

// ===== 媒体库选择弹窗 =====
const pickerVisible = ref(false)
const pickerScene = ref('cover') // cover | gallery | video | rich
const pickerType = computed(() => (pickerScene.value === 'video' ? 'video' : 'image'))
const pickerMultiple = computed(() => pickerScene.value === 'gallery' || pickerScene.value === 'rich')

function openPicker(scene) {
  pickerScene.value = scene
  pickerVisible.value = true
}

function onPickerSelect(items) {
  const urls = (items || []).map(i => i.url).filter(Boolean)
  if (!urls.length) return
  switch (pickerScene.value) {
    case 'cover':
      form.coverImage = urls[0]
      break
    case 'gallery':
      urls.forEach(u => { if (!galleryList.value.includes(u)) galleryList.value.push(u) })
      break
    case 'video':
      form.videoUrl = urls[0]
      break
    case 'rich':
      if (editorRef.value) {
        editorRef.value.dangerouslyInsertHtml(urls.map(u => `<img src="${u}" style="max-width:100%;"/>`).join(''))
      }
      break
  }
  ElMessage.success(`已插入 ${urls.length} 项`)
}

/** 货号安全化（仅 ASCII 字母数字下划线连字符），用于上传文件命名 */
function safeProductNo() {
  const no = String(form.productNo || '').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 40)
  return no || 'NA'
}

/** 按货号+时间戳重命名上传文件 */
function stampName(file) {
  const ext = (String(file?.name || '').split('.').pop() || 'bin').toLowerCase()
  return `${safeProductNo()}_${Date.now()}.${ext}`
}

const viewVisible = ref(false)
const viewProduct = ref({})
const previewGallery = computed(() => {
  try { return JSON.parse(viewProduct.value.imageList || '[]') } catch { return [] }
})

// 用户端详情页可配置展示的字段
const FIELD_OPTIONS = [
  { key: 'productNo', label: '货号' },
  { key: 'productName', label: '品名' },
  { key: 'brand', label: '品牌' },
  { key: 'crotchType', label: '裆型' },
  { key: 'thickness', label: '厚度' },
  { key: 'color', label: '颜色' },
  { key: 'size', label: '尺码' },
  { key: 'spec', label: '规格' },
  { key: 'material', label: '材质' },
  { key: 'composition', label: '成分' },
  { key: 'feature', label: '特点' },
  { key: 'origin', label: '产地' },
  { key: 'category', label: '分类' },
  { key: 'sortOrder', label: '排序' },
  { key: 'createTime', label: '创建时间' }
]
const DEFAULT_DISPLAY_FIELDS = FIELD_OPTIONS
  .filter(f => f.key !== 'sortOrder' && f.key !== 'createTime')
  .map(f => f.key)
const displayFieldsModel = ref([...DEFAULT_DISPLAY_FIELDS])

// 品牌 / 分类字典
const brandOptions = ref([])
const categoryOptions = ref([])
const dictVisible = ref(false)
const dictType = ref('brand')
const dictItems = ref([])
const dictInput = ref('')

async function loadDicts() {
  const [b, c] = await Promise.all([
    request.get('/admin/dict', { params: { type: 'brand' } }),
    request.get('/admin/dict', { params: { type: 'category' } })
  ])
  brandOptions.value = b.data
  categoryOptions.value = c.data
}

function openDict(type) {
  dictType.value = type
  dictVisible.value = true
  dictInput.value = ''
  loadDictItems()
}

async function loadDictItems() {
  const res = await request.get('/admin/dict', { params: { type: dictType.value } })
  dictItems.value = res.data
}

async function addDict() {
  const name = dictInput.value.trim()
  if (!name) return
  await request.post('/admin/dict', null, { params: { type: dictType.value, name } })
  dictInput.value = ''
  ElMessage.success('已添加')
  await Promise.all([loadDictItems(), loadDicts()])
}

async function removeDict(row) {
  await request.delete(`/admin/dict/${row.id}`)
  ElMessage.success('已删除')
  await Promise.all([loadDictItems(), loadDicts()])
}

// 批量导入
const importVisible = ref(false)
const templateLoading = ref(false)
const importing = ref(false)
const importResult = ref(null)
const importFile = ref(null)

function openImport() {
  importVisible.value = true
  importResult.value = null
  importFile.value = null
}

async function downloadTemplate() {
  templateLoading.value = true
  try {
    // 走 axios 下载（自动带 Authorization 头），再本地落成文件
    const res = await request.get('/admin/product-import/template', { responseType: 'blob' })
    const url = URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = '产品导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } finally {
    templateLoading.value = false
  }
}

function onImportFileChange(uploadFile) {
  importFile.value = uploadFile.raw
  importResult.value = null
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 .xlsx 文件')
    return
  }
  if (importFile.value.size > 5 * 1024 * 1024) {
    ElMessage.warning('文件大小不能超过 5MB')
    return
  }
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    const res = await request.post('/admin/product-import', fd)
    importResult.value = res.data
    if (res.data.success > 0) {
      ElMessage.success(`导入完成：成功 ${res.data.success} 行，失败 ${res.data.failed} 行`)
      load()
    } else {
      ElMessage.warning('没有成功导入任何产品，请查看错误明细')
    }
  } finally {
    importing.value = false
  }
}

const toolbarConfig = { excludeKeys: ['group-video', 'uploadVideo', 'insertVideo'] }
const editorConfig = {
  placeholder: '请输入图文详情...',
  MENU_CONF: {
    uploadImage: {
      // 自定义上传：前端压缩（>2MB）→ 按「货号_时间戳」重命名 → 走后端 /admin/media/image
      async customUpload(file, insertFn) {
        try {
          const f = await compressImage(file)
          const fd = new FormData()
          fd.append('file', f, stampName(f))
          const res = await request.post('/admin/media/image', fd, {
            onUploadProgress(e) {
              const p = e.total ? Math.round((e.loaded / e.total) * 100) : 0
              uploadState.value = { active: true, stage: 'upload', percent: p, text: `详情图上传中 ${p}%` }
            }
          })
          insertFn(res.data.fileUrl, f.name, res.data.fileUrl)
        } catch (e) {
          ElMessage.error('图片上传失败：' + (e?.response?.data?.message || e?.message || '请重试'))
        } finally {
          uploadState.value = { active: false, stage: '', percent: 0, text: '' }
        }
      }
    }
  }
}

async function load() {
  loading.value = true
  try {
    const res = await request.get('/admin/product/page', { params: query })
    list.value = res.data.records
    total.value = Number(res.data.total)
  } finally {
    loading.value = false
  }
}

async function openView(row) {
  const res = await request.get(`/admin/product/${row.id}`)
  viewProduct.value = res.data
  viewVisible.value = true
}

function openEdit(row) {
  Object.keys(form).forEach(k => delete form[k])
  coverUrlInput.value = ''
  galleryUrlInput.value = ''
  if (row) {
    Object.assign(form, row)
    try { galleryList.value = JSON.parse(row.imageList || '[]') } catch { galleryList.value = [] }
    // 已保存的轮播第一张就是封面时，复选框默认保持勾选
    coverAsFirst.value = !!row.coverImage && galleryList.value[0] === row.coverImage
  } else {
    Object.assign(form, { shelfStatus: 1, sortOrder: 100, description: '' })
    galleryList.value = []
    coverAsFirst.value = true
  }
  let saved = []
  try { saved = JSON.parse(form.displayFields || '[]') } catch { saved = [] }
  displayFieldsModel.value = saved.length ? saved : [...DEFAULT_DISPLAY_FIELDS]
  editVisible.value = true
}

function isImageUrl(url) {
  return /^https?:\/\/\S+/i.test(url)
}
function addCoverUrl() {
  const url = coverUrlInput.value.trim()
  if (!url) return
  if (!isImageUrl(url)) { ElMessage.warning('请输入以 http/https 开头的图片地址'); return }
  form.coverImage = url
  coverUrlInput.value = ''
  ElMessage.success('已设置封面图')
}

function addGalleryUrl() {
  const url = galleryUrlInput.value.trim()
  if (!url) return
  if (!isImageUrl(url)) { ElMessage.warning('请输入以 http/https 开头的图片地址'); return }
  galleryList.value.push(url)
  galleryUrlInput.value = ''
  ElMessage.success('已添加轮播图')
}

// ===== 上传状态（压缩/上传两阶段进度） =====
const uploadState = ref({ active: false, stage: '', percent: 0, text: '' })
const IMG_LIMIT = 2 * 1024 * 1024   // 图片超 2MB 自动前端压缩
const VIDEO_LIMIT = 50 * 1024 * 1024 // 视频建议上限 50MB
const VIDEO_MAX = 200 * 1024 * 1024  // 视频硬上限 200MB

/** 格式化 ETA 文本 */
function etaText(startTime, percent) {
  if (percent <= 0) return ''
  const elapsed = (Date.now() - startTime) / 1000
  const remain = elapsed / (percent / 100) - elapsed
  if (!isFinite(remain) || remain < 1) return ''
  return `，预计还需 ${Math.ceil(remain)}s`
}

/** 用 compressorjs 在浏览器端压缩图片到 2MB 以内 */
function compressImage(file) {
  return new Promise((resolve) => {
    if (file.size <= IMG_LIMIT) return resolve(file)
    const startTime = Date.now()
    let fake = 0
    const timer = setInterval(() => {
      fake = Math.min(fake + 8, 90)
      uploadState.value = { active: true, stage: 'compress', percent: fake, text: `正在压缩图片 ${fake}%${etaText(startTime, fake)}` }
    }, 150)
    // 动态质量：目标 2MB，按超大程度递减
    const quality = file.size > 8 * 1024 * 1024 ? 0.5 : file.size > 4 * 1024 * 1024 ? 0.6 : 0.72
    new Compressor(file, {
      quality,
      maxWidth: 1600,
      maxHeight: 1600,
      mimeType: 'image/jpeg',
      convertSize: 0, // 超过 convertSize 的 PNG 也会转 jpeg
      success(result) {
        clearInterval(timer)
        // 压缩结果仍超限时二次降质压缩一次
        if (result.size > IMG_LIMIT) {
          new Compressor(result, {
            quality: 0.5, maxWidth: 1200, maxHeight: 1200, mimeType: 'image/jpeg',
            success(r2) { clearInterval(timer); resolve(new File([r2], file.name.replace(/\.\w+$/, '.jpg'), { type: 'image/jpeg' })) },
            error() { clearInterval(timer); resolve(result) }
          })
          return
        }
        resolve(new File([result], file.name.replace(/\.\w+$/, '.jpg'), { type: 'image/jpeg' }))
      },
      error() { clearInterval(timer); resolve(file) } // 压缩失败兜底用原文件
    })
  })
}

async function uploadImage(option, type) {
  uploadState.value = { active: true, stage: 'compress', percent: 0, text: '正在检查图片...' }
  try {
    const file = await compressImage(option.file)
    const startTime = Date.now()
    const fd = new FormData()
    fd.append('file', file, stampName(file))
    const res = await request.post('/admin/media/image', fd, {
      onUploadProgress(e) {
        const p = e.total ? Math.round((e.loaded / e.total) * 100) : 0
        uploadState.value = { active: true, stage: 'upload', percent: p, text: `正在上传 ${p}%${etaText(startTime, p)}` }
      }
    })
    const url = res.data.fileUrl
    if (type === 'cover') form.coverImage = url
    else galleryList.value.push(url)
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error('上传失败：' + (e?.response?.data?.message || e?.message || '请重试'))
  } finally {
    uploadState.value = { active: false, stage: '', percent: 0, text: '' }
  }
}

function beforeVideoUpload(file) {
  if (file.size > VIDEO_MAX) {
    ElMessage.warning('视频文件不能超过 200MB，请压缩后再上传')
    return false
  }
  if (file.size > VIDEO_LIMIT) {
    ElMessage.warning(`视频 ${(file.size / 1048576).toFixed(1)}MB 超过建议值 50MB，仍可上传但加载较慢，建议先用剪映等工具压缩`)
  }
  return true
}

async function uploadVideo(option) {
  videoUploading.value = true
  const startTime = Date.now()
  uploadState.value = { active: true, stage: 'upload', percent: 0, text: '正在上传 0%' }
  try {
    const fd = new FormData()
    fd.append('file', option.file, stampName(option.file))
    const res = await request.post('/admin/media/video', fd, {
      onUploadProgress(e) {
        const p = e.total ? Math.round((e.loaded / e.total) * 100) : 0
        uploadState.value = { active: true, stage: 'upload', percent: p, text: `正在上传视频 ${p}%${etaText(startTime, p)}` }
      }
    })
    form.videoUrl = res.data.fileUrl
    ElMessage.success('视频上传成功')
  } catch (e) {
    ElMessage.error('视频上传失败：' + (e?.response?.data?.message || e?.message || '请重试'))
  } finally {
    videoUploading.value = false
    uploadState.value = { active: false, stage: '', percent: 0, text: '' }
  }
}

async function save() {
  // 输入框里已填但未点「添加」的地址，保存时自动应用
  if (coverUrlInput.value.trim()) addCoverUrl()
  if (galleryUrlInput.value.trim()) addGalleryUrl()
  if (!form.productNo || !form.productName) {
    ElMessage.warning('产品编号和名称必填')
    return
  }
  saving.value = true
  try {
    // 统一收敛地址格式：完整 http(s) 地址原样保留，相对地址补 / 开头
    if (form.coverImage) form.coverImage = normalizeMediaInput(form.coverImage)
    if (form.videoUrl) form.videoUrl = normalizeMediaInput(form.videoUrl)
    let gallery = galleryList.value.map(normalizeMediaInput).filter(Boolean)
    // 勾选后把封面图提到轮播第一张（已存在则不重复添加）
    if (coverAsFirst.value && form.coverImage) {
      gallery = [form.coverImage, ...gallery.filter(u => u !== form.coverImage)]
    }
    form.imageList = JSON.stringify(gallery)
    form.displayFields = JSON.stringify(displayFieldsModel.value)
    if (form.id) {
      await request.put('/admin/product', form)
    } else {
      await request.post('/admin/product', form)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleShelf(row) {
  await request.put('/admin/product', { ...row, shelfStatus: row.shelfStatus === 1 ? 0 : 1 })
  ElMessage.success('操作成功')
  load()
}

async function remove(row) {
  await request.delete(`/admin/product/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadDicts()
})
onBeforeUnmount(() => editorRef.value?.destroy())
</script>

<style scoped>
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; }
.gallery { display: flex; gap: 8px; margin-left: 12px; flex-wrap: wrap; }
.gallery-item { position: relative; }
.gallery-item .del {
  position: absolute; top: -6px; right: -6px;
  background: #f56c6c; color: #fff; border-radius: 50%;
  cursor: pointer; font-size: 12px; padding: 2px;
}
.field-tip { width: 100%; margin-top: 6px; color: #909399; font-size: 12px; line-height: 1.6; }
.dict-add { display: flex; gap: 8px; margin-bottom: 12px; }
.import-tips { color: #606266; font-size: 13px; line-height: 1.8; margin-bottom: 12px; }
.import-tips p { margin: 0; }
.import-actions { display: flex; align-items: flex-start; gap: 12px; }
.video-preview { width: 320px; height: auto; max-height: 80vh; margin-top: 10px; border-radius: 4px; background: #000; object-fit: contain; display: block; }

.upload-progress { width: 100%; display: flex; flex-direction: column; gap: 6px; }
.upload-progress__text { font-size: 12px; color: #606266; }
.editor-extra { width: 100%; display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.img-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 12px; background: #f2f3f5; text-align: center; padding: 4px; }
.img-err--lg { min-height: 160px; font-size: 13px; color: #909399; }
.media-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

/* 产品预览 */
.preview__cover { width: 100%; max-height: 320px; border-radius: 6px; background: #f5f7fa; }
.preview__head { display: flex; align-items: center; gap: 8px; margin: 12px 0; flex-wrap: wrap; }
.preview__name { font-size: 18px; font-weight: 600; color: #303133; margin-right: 4px; }
.preview__block { margin-top: 18px; }
.preview__block-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #409eff; }
.preview__gallery { display: flex; gap: 8px; flex-wrap: wrap; }
.preview__gallery-img { width: 96px; height: 96px; border-radius: 4px; background: #f5f7fa; }
.preview__video { width: 100%; height: auto; max-height: 80vh; border-radius: 4px; background: #000; object-fit: contain; display: block; }
.preview__rich { font-size: 14px; line-height: 1.7; color: #303133; }
.preview__rich :deep(img) { max-width: 100%; height: auto; }
.preview__empty { color: #909399; font-size: 13px; }
</style>

<style>
/* wangEditor 弹层（网络图片/链接等 modal、下拉面板、悬浮条）默认 z-index 过低，
   在 el-dialog（z-index 2000+）中会被遮住，统一提升到对话框之上 */
.w-e-modal,
.w-e-drop-panel,
.w-e-hover-bar,
.w-e-panel-content-color,
.w-e-toolbar-tooltip {
  z-index: 4000 !important;
}
</style>
