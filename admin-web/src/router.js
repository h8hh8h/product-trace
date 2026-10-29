import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', name: 'Login', component: () => import('./views/Login.vue') },
  {
    path: '/',
    component: () => import('./layout/Index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '数据总览' } },
      { path: 'product', name: 'Product', component: () => import('./views/ProductList.vue'), meta: { title: '产品管理' } },
      { path: 'code', name: 'Code', component: () => import('./views/CodeManage.vue'), meta: { title: '二维码管理' } },
      { path: 'stats', name: 'Stats', component: () => import('./views/ScanStats.vue'), meta: { title: '扫码统计' } },
      { path: 'site-config', name: 'SiteConfig', component: () => import('./views/SiteConfig.vue'), meta: { title: '站点设置' } },
      { path: 'audit-log', name: 'AuditLog', component: () => import('./views/AuditLog.vue'), meta: { title: '操作审计' } },
      { path: 'system/role', name: 'Role', component: () => import('./views/system/Role.vue'), meta: { title: '角色管理' } },
      { path: 'system/user', name: 'AdminUser', component: () => import('./views/system/AdminUser.vue'), meta: { title: '账号管理' } },
      { path: 'system/storage', name: 'StorageConfig', component: () => import('./views/StorageConfig.vue'), meta: { title: '存储配置' } },
      { path: 'code-print', name: 'CodePrint', component: () => import('./views/CodePrint.vue'), meta: { title: '二维码标签打印' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('ADMIN_TOKEN')
  if (to.path !== '/login' && !token) {
    return '/login'
  }
})

export default router
