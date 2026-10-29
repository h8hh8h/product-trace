<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <h2 class="title">产品溯源管理后台</h2>
      <el-form :model="form" @keyup.enter="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../request'

const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '' })

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await request.post('/admin/auth/login', form)
    localStorage.setItem('ADMIN_TOKEN', res.data.token)
    localStorage.setItem('ADMIN_NICKNAME', res.data.nickname || res.data.username)
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b5876 100%);
}
.login-card {
  width: 380px;
  padding: 10px 20px 20px;
}
.title {
  text-align: center;
  color: #303133;
  margin-bottom: 24px;
}
</style>
