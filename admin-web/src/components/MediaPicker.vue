<template>
  <el-dialog v-model="visible" :title="title" width="860px" destroy-on-close @open="reload">
    <div class="mp-toolbar">
      <el-radio-group v-model="query.type" size="default" @change="reload">
        <el-radio-button value="image">图片</el-radio-button>
        <el-radio-button value="video">视频</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="query.keyword"
        placeholder="输入货号或文件名搜索"
        clearable
        style="width: 260px"
        @keyup.enter="reload"
        @clear="reload"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" plain @click="reload">搜索</el-button>
      <span class="mp-tip" v-if="multiple">可勾选 {{ maxCount }} 项，点击「确定」回填</span>
    </div>

    <div v-loading="loading" class="mp-grid-wrap">
      <div v-if="list.length" class="mp-grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="mp-cell"
          :class="{ 'is-active': isSelected(item) }"
          @click="toggle(item)"
        >
          <video
            v-if="item.mediaType === 'video'"
            :src="resolveMediaUrl(item.fileUrl)"
            class="mp-thumb"
            preload="metadata"
            muted
          />
          <el-image v-else :src="resolveMediaUrl(item.fileUrl)" fit="contain" class="mp-thumb" lazy>
            <template #error><div class="mp-thumb-err">加载失败</div></template>
          </el-image>
          <div class="mp-name" :title="item.fileName">{{ item.fileName }}</div>
          <div class="mp-size">{{ formatSize(item.fileSize) }}</div>
          <el-icon v-if="multiple && isSelected(item)" class="mp-check"><CircleCheckFilled /></el-icon>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="媒体库为空，先去上传或检查存储配置" :image-size="80" />
    </div>

    <div class="mp-footer">
      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
      />
      <div v-if="multiple" class="mp-actions">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedItems.length" @click="confirmMulti">
          确定{{ selectedItems.length ? `（已选 ${selectedItems.length}）` : '' }}
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, CircleCheckFilled } from '@element-plus/icons-vue'
import request from '../request'
import { resolveMediaUrl } from '../media'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  type: { type: String, default: 'image' },        // image | video
  multiple: { type: Boolean, default: false },
  maxCount: { type: Number, default: 9 }
})
const emit = defineEmits(['update:modelValue', 'select'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})
const title = computed(() => (props.type === 'video' ? '从媒体库选择视频' : '从媒体库选择图片'))

const query = reactive({ type: props.type, keyword: '', page: 1, size: 24 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const selectedItems = ref([])

watch(() => props.type, v => { query.type = v })

async function load() {
  loading.value = true
  try {
    const res = await request.get('/admin/media/list', { params: { type: query.type, keyword: query.keyword, page: query.page, size: query.size } })
    list.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } catch (e) {
    ElMessage.error('媒体库加载失败：' + (e?.response?.data?.message || e?.message || '请重试'))
  } finally {
    loading.value = false
  }
}

function reload() {
  query.page = 1
  load()
}

function isSelected(item) {
  return selectedItems.value.some(s => s.id === item.id)
}

function toggle(item) {
  if (!props.multiple) {
    // 单选：点击即返回
    emit('select', [{ id: item.id, url: item.fileUrl, fileName: item.fileName, mediaType: item.mediaType }])
    visible.value = false
    return
  }
  const idx = selectedItems.value.findIndex(s => s.id === item.id)
  if (idx >= 0) selectedItems.value.splice(idx, 1)
  else if (selectedItems.value.length >= props.maxCount) ElMessage.warning(`最多选择 ${props.maxCount} 项`)
  else selectedItems.value.push({ id: item.id, url: item.fileUrl, fileName: item.fileName, mediaType: item.mediaType })
}

function confirmMulti() {
  if (!selectedItems.value.length) return
  emit('select', selectedItems.value.slice())
  selectedItems.value = []
  visible.value = false
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes > 1048576) return (bytes / 1048576).toFixed(1) + ' MB'
  return Math.round(bytes / 1024) + ' KB'
}
</script>

<style scoped>
.mp-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.mp-tip { font-size: 12px; color: #909399; margin-left: auto; }
.mp-grid-wrap { min-height: 320px; max-height: 56vh; overflow-y: auto; }
.mp-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.mp-cell {
  position: relative; border: 2px solid transparent; border-radius: 8px; padding: 6px;
  background: #f7f8fa; cursor: pointer; transition: border-color 0.15s;
}
.mp-cell:hover { border-color: #c0c4cc; }
.mp-cell.is-active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.mp-thumb { width: 100%; height: 100px; display: block; border-radius: 6px; background: #eef0f3; object-fit: contain; }
.mp-thumb-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #c0c4cc; font-size: 12px; }
.mp-name { margin-top: 6px; font-size: 12px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mp-size { font-size: 11px; color: #909399; }
.mp-check { position: absolute; top: 8px; right: 8px; color: var(--el-color-primary); font-size: 20px; background: #fff; border-radius: 50%; }
.mp-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 14px; }
</style>
