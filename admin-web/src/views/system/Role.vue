<template>
  <div class="role-page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="角色名称">
          <el-input v-model="query.roleName" placeholder="模糊搜索" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right; margin-right: 0">
          <el-button type="primary" @click="openEdit()">新增角色</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="roleName" label="角色名称" width="140" />
        <el-table-column prop="roleCode" label="角色编码" width="140" />
        <el-table-column prop="description" label="描述" min-width="180">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="权限模块" min-width="260">
          <template #default="{ row }">
            <el-tag v-for="p in parsePerms(row.permissions)" :key="p" size="small" style="margin-right: 4px">
              {{ permLabel(p) }}
            </el-tag>
            <span v-if="parsePerms(row.permissions).length === 0">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
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

    <el-dialog v-model="editVisible" :title="form.id ? '编辑角色' : '新增角色'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" maxlength="64" />
        </el-form-item>
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" maxlength="64" placeholder="唯一编码，如 operator" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" maxlength="255" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="权限模块">
          <el-checkbox-group v-model="form.permList">
            <el-checkbox v-for="m in PERM_MODULES" :key="m.key" :value="m.key">{{ m.label }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../request'

const PERM_MODULES = [
  { key: 'product', label: '产品管理' },
  { key: 'code', label: '二维码管理' },
  { key: 'stats', label: '扫码统计' },
  { key: 'config', label: '站点配置' },
  { key: 'audit', label: '操作审计' },
  { key: 'media', label: '媒体库' },
  { key: 'system', label: '系统管理' }
]

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const editVisible = ref(false)

const query = reactive({ current: 1, size: 20, roleName: '' })
const form = reactive({ id: null, roleName: '', roleCode: '', description: '', status: 1, permList: [] })

function permLabel(key) {
  const m = PERM_MODULES.find(x => x.key === key)
  return m ? m.label : key
}

function parsePerms(json) {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch (e) {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const params = { current: query.current, size: query.size }
    if (query.roleName) params.roleName = query.roleName
    const res = await request.get('/admin/role/page', { params })
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
  query.roleName = ''
  search()
}

function openEdit(row) {
  if (row) {
    form.id = row.id
    form.roleName = row.roleName
    form.roleCode = row.roleCode
    form.description = row.description || ''
    form.status = row.status
    form.permList = parsePerms(row.permissions)
  } else {
    form.id = null
    form.roleName = ''
    form.roleCode = ''
    form.description = ''
    form.status = 1
    form.permList = []
  }
  editVisible.value = true
}

async function save() {
  if (!form.roleName || !form.roleCode) {
    ElMessage.warning('请填写角色名称和编码')
    return
  }
  saving.value = true
  try {
    const payload = {
      id: form.id,
      roleName: form.roleName,
      roleCode: form.roleCode,
      description: form.description,
      status: form.status,
      permissions: JSON.stringify(form.permList)
    }
    if (form.id) {
      await request.put('/admin/role', payload)
    } else {
      await request.post('/admin/role', payload)
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, '提示', { type: 'warning' })
  await request.delete(`/admin/role/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.role-page { display: flex; flex-direction: column; gap: 12px; }
.filter-form :deep(.el-form-item) { margin-bottom: 8px; }
.table-card :deep(.el-card__body) { padding: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
