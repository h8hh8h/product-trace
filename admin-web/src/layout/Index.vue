<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="logo">产品溯源后台</div>
      <el-menu :default-active="$route.path" router background-color="#1f2d3d" text-color="#bfcbd9" active-text-color="#409eff">
        <el-menu-item index="/dashboard"><el-icon><DataLine /></el-icon><span>数据总览</span></el-menu-item>
        <el-menu-item v-if="hasPerm('product')" index="/product"><el-icon><Goods /></el-icon><span>产品管理</span></el-menu-item>
        <el-menu-item v-if="hasPerm('code')" index="/code"><el-icon><Grid /></el-icon><span>二维码管理</span></el-menu-item>
        <el-menu-item v-if="hasPerm('stats')" index="/stats"><el-icon><Document /></el-icon><span>扫码统计</span></el-menu-item>
        <el-menu-item v-if="hasPerm('config')" index="/site-config"><el-icon><Setting /></el-icon><span>站点设置</span></el-menu-item>
        <el-menu-item v-if="hasPerm('audit')" index="/audit-log"><el-icon><Document /></el-icon><span>操作审计</span></el-menu-item>
        <el-sub-menu v-if="hasPerm('system')" index="system">
          <template #title><el-icon><Tools /></el-icon><span>系统管理</span></template>
          <el-menu-item index="/system/role">角色管理</el-menu-item>
          <el-menu-item index="/system/user">账号管理</el-menu-item>
          <el-menu-item index="/system/storage">存储配置</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>{{ $route.meta.title }}</span>
        <el-dropdown @command="handleCommand">
          <span class="user">{{ nickname }}<el-icon><ArrowDown /></el-icon></span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="pwd">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="pwdVisible" title="修改密码" width="400px">
    <el-form :model="pwdForm" label-width="80px">
      <el-form-item label="原密码"><el-input v-model="pwdForm.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="pwdForm.newPassword" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="pwdLoading" @click="changePwd">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../request'

const router = useRouter()
const nickname = ref(localStorage.getItem('ADMIN_NICKNAME') || '管理员')
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

// null 表示未加载/后端未返回（超管或旧版本），菜单全部显示；数组则按模块 key 过滤
const permissions = ref(null)

function hasPerm(module) {
  if (!Array.isArray(permissions.value)) return true
  return permissions.value.includes(module)
}

async function loadPermissions() {
  try {
    const res = await request.get('/admin/auth/info')
    permissions.value = Array.isArray(res.data?.permissions) ? res.data.permissions : null
  } catch (e) {
    /* 获取权限失败时保持全显，向后兼容 */
  }
}

onMounted(loadPermissions)

function handleCommand(cmd) {
  if (cmd === 'logout') {
    request.post('/admin/auth/logout').catch(() => {})
    localStorage.removeItem('ADMIN_TOKEN')
    router.push('/login')
  } else if (cmd === 'pwd') {
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdVisible.value = true
  }
}

async function changePwd() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写完整')
    return
  }
  pwdLoading.value = true
  try {
    await request.post('/admin/auth/password', pwdForm)
    ElMessage.success('密码已修改，请重新登录')
    localStorage.removeItem('ADMIN_TOKEN')
    router.push('/login')
  } finally {
    pwdLoading.value = false
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.el-aside { background-color: #1f2d3d; }
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
}
.el-menu { border-right: none; }
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e4e7ed;
  font-size: 16px;
}
.user {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #409eff;
}
.el-main { background-color: #f5f6f8; }
</style>
