<template>
  <div class="admin-user-page">
    <el-card shadow="never">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item label="用户名">
          <el-input v-model="query.username" placeholder="模糊搜索" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right; margin-right: 0">
          <el-button type="primary" @click="addVisible = true">新增管理员</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="140">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.roleId === null || row.roleId === undefined" type="danger" size="small">超级管理员</el-tag>
            <el-tag v-else size="small">{{ row.roleName || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openReset(row)">重置密码</el-button>
            <el-button link type="primary" @click="openAssign(row)">分配角色</el-button>
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

    <el-dialog v-model="addVisible" title="新增管理员" width="440px">
      <el-form :model="addForm" label-width="90px">
        <el-form-item label="用户名" required>
          <el-input v-model="addForm.username" maxlength="64" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="addForm.password" type="password" show-password placeholder="≥8位，含字母和数字" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addForm.roleId" placeholder="不选则为超级管理员" clearable style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAdd">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="440px">
      <el-form :model="resetForm" label-width="90px">
        <el-form-item label="用户名">
          <span>{{ resetForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="resetForm.password" type="password" show-password placeholder="≥8位，含字母和数字" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReset">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="分配角色" width="440px">
      <el-form :model="assignForm" label-width="90px">
        <el-form-item label="用户名">
          <span>{{ assignForm.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="assignForm.roleId" placeholder="不选则为超级管理员" clearable style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.roleName" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssign">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../request'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const roles = ref([])

const addVisible = ref(false)
const resetVisible = ref(false)
const assignVisible = ref(false)

const query = reactive({ current: 1, size: 20, username: '' })
const addForm = reactive({ username: '', password: '', roleId: null })
const resetForm = reactive({ id: null, username: '', password: '' })
const assignForm = reactive({ id: null, username: '', roleId: null })

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : '-'
}

async function load() {
  loading.value = true
  try {
    const params = { current: query.current, size: query.size }
    if (query.username) params.username = query.username
    const res = await request.get('/admin/admin-user/page', { params })
    rows.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await request.get('/admin/role/list')
    roles.value = res.data || []
  } catch (e) {
    /* 角色下拉失败不影响主表格 */
  }
}

function search() {
  query.current = 1
  load()
}

function reset() {
  query.username = ''
  search()
}

async function saveAdd() {
  if (!addForm.username || !addForm.password) {
    ElMessage.warning('请填写用户名和初始密码')
    return
  }
  saving.value = true
  try {
    await request.post('/admin/admin-user', { ...addForm })
    ElMessage.success('已创建，首次登录需修改密码')
    addVisible.value = false
    addForm.username = ''
    addForm.password = ''
    addForm.roleId = null
    load()
  } finally {
    saving.value = false
  }
}

function openReset(row) {
  resetForm.id = row.id
  resetForm.username = row.username
  resetForm.password = ''
  resetVisible.value = true
}

async function saveReset() {
  if (!resetForm.password) {
    ElMessage.warning('请填写新密码')
    return
  }
  saving.value = true
  try {
    await request.put(`/admin/admin-user/${resetForm.id}/password`, { password: resetForm.password })
    ElMessage.success('密码已重置，该用户下次登录需修改密码')
    resetVisible.value = false
  } finally {
    saving.value = false
  }
}

function openAssign(row) {
  assignForm.id = row.id
  assignForm.username = row.username
  assignForm.roleId = row.roleId ?? null
  assignVisible.value = true
}

async function saveAssign() {
  saving.value = true
  try {
    await request.put(`/admin/admin-user/${assignForm.id}/role`, { roleId: assignForm.roleId })
    ElMessage.success('角色已更新')
    assignVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.admin-user-page { display: flex; flex-direction: column; gap: 12px; }
.filter-form :deep(.el-form-item) { margin-bottom: 8px; }
.table-card :deep(.el-card__body) { padding: 12px; }
.pager { margin-top: 12px; justify-content: flex-end; }
</style>
