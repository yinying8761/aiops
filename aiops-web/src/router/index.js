import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '告警大屏', icon: 'Odometer' }
      },
      {
        path: 'alerts',
        name: 'alerts',
        component: () => import('@/views/AlertList.vue'),
        meta: { title: '告警中心', icon: 'Warning' }
      },
      {
        path: 'alerts/:id/analyze',
        name: 'analyze',
        component: () => import('@/views/AgentAnalyze.vue'),
        meta: { title: 'AI 根因分析', hidden: true }
      },
      {
        path: 'tickets',
        name: 'tickets',
        component: () => import('@/views/TicketList.vue'),
        meta: { title: '工单处置', icon: 'Tickets' }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/Knowledge.vue'),
        meta: { title: '知识库', icon: 'Collection' }
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('@/views/Reports.vue'),
        meta: { title: '统计报表', icon: 'DataAnalysis' }
      },
      {
        path: 'demo',
        name: 'demo',
        component: () => import('@/views/DemoConsole.vue'),
        meta: { title: '故障演练台', icon: 'MagicStick' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UserManage.vue'),
        meta: { title: '用户管理', icon: 'User', adminOnly: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('aiops_token')
  if (to.meta.public) {
    next()
    return
  }
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  // 管理员页面权限校验（前端兜底，后端仍会校验）
  if (to.meta.adminOnly) {
    const user = JSON.parse(localStorage.getItem('aiops_user') || 'null')
    if (!(user?.roles || []).includes('ADMIN')) {
      next({ path: '/dashboard' })
      return
    }
  }
  document.title = to.meta.title
    ? `${to.meta.title} - AIOps 平台`
    : 'AIOps 平台'
  next()
})

export default router
