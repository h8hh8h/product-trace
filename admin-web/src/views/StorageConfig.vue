<template>
  <el-card v-loading="loading">
    <template #header>
      <div class="card-head">
        <span>存储配置（图片 / 视频 / 二维码保存位置）</span>
        <div>
          <el-button type="warning" plain :loading="testing" :disabled="form.storageType !== 'oss'" @click="testOss">测试 OSS 连接</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </div>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="保存后立即生效，无需重启服务。上传产品图片、上传视频、生成普通码/一物一码二维码都会按此配置落盘。"
      style="margin-bottom: 18px"
    />

    <el-form label-width="150px">
      <el-form-item label="存储方式">
        <el-radio-group v-model="form.storageType">
          <el-radio value="local">本地服务器存储（默认）</el-radio>
          <el-radio value="oss">阿里云 OSS 存储</el-radio>
        </el-radio-group>
      </el-form-item>

      <template v-if="form.storageType === 'local'">
        <el-form-item label="本地存储路径">
          <el-input v-model="form.localRootPath" placeholder="/www/wwwroot/product-trace/upload/" />
          <div class="tip">
            必须是服务器上的绝对路径，且 Web 用户（www）有写入权限。该目录必须与 Nginx 的
            <code>location /upload/</code> 的 alias 一致，否则图片、二维码无法访问。
          </div>
        </el-form-item>
        <el-form-item label="访问 URL 前缀">
          <el-input v-model="form.localUrlPrefix" placeholder="/upload" />
          <div class="tip">
            填写 <code>/upload</code> 表示相对路径（后台与 H5 站点都用 Nginx 直出）；填写
            <code>https://api.你的域名.com/upload</code> 表示绝对地址。留空等同于 /upload。
          </div>
        </el-form-item>
      </template>

      <template v-else>
        <el-form-item label="AccessKey ID">
          <el-input v-model="form.ossAccessKeyId" placeholder="LTAI..." autocomplete="off" />
        </el-form-item>
        <el-form-item label="AccessKey Secret">
          <el-input v-model="form.ossAccessKeySecret" type="password" show-password autocomplete="new-password"
                    placeholder="留空或显示 ****** 表示保持原值不变" />
        </el-form-item>
        <el-form-item label="Bucket 名称">
          <el-input v-model="form.ossBucketName" placeholder="例如 product-trace" />
        </el-form-item>
        <el-form-item label="Endpoint">
          <el-input v-model="form.ossEndpoint" placeholder="oss-cn-hangzhou.aliyuncs.com" />
          <div class="tip">不要带 https:// 前缀也可以，系统会自动补全为 https://{bucket}.{endpoint}</div>
        </el-form-item>
        <el-form-item label="访问域名 / CDN">
          <el-input v-model="form.ossUrlPrefix" placeholder="可选，例如 https://cdn.yourdomain.com" />
          <div class="tip">留空则使用 OSS 默认域名；配置 CDN 后二维码与图片 URL 都走该域名</div>
        </el-form-item>
        <el-form-item label="存储 Bucket 权限">
          <el-tag type="warning">请将 Bucket 读权限设为「公共读」，否则 H5/小程序无法直接加载图片与二维码</el-tag>
        </el-form-item>
      </template>
    </el-form>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../request'

const form = reactive({
  storageType: 'local',
  localRootPath: '/www/wwwroot/product-trace/upload',
  localUrlPrefix: '/upload',
  ossAccessKeyId: '',
  ossAccessKeySecret: '',
  ossBucketName: '',
  ossEndpoint: '',
  ossUrlPrefix: ''
})
const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await request.get('/admin/storage-config')
    Object.assign(form, res.data || {})
    form.storageType = form.storageType || 'local'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    await request.post('/admin/storage-config/save', { ...form })
    ElMessage.success('存储配置已保存并立即生效')
    await load()
  } finally {
    saving.value = false
  }
}

async function testOss() {
  testing.value = true
  try {
    // 先保存，保证测的是当前表单里的参数
    await request.post('/admin/storage-config/save', { ...form })
    const res = await request.post('/admin/storage-config/test')
    ElMessage.success(res.data || 'OSS 连接正常')
    await load()
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.tip { width: 100%; margin-top: 6px; color: #909399; font-size: 12px; line-height: 1.6; }
.tip code { background: #f5f7fa; padding: 1px 4px; border-radius: 3px; color: #606266; }
</style>
