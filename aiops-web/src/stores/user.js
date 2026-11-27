import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '@/api/auth'

/**
 * 用户状态。Token 存 localStorage，刷新页面后可恢复登录态。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('aiops_token') || '',
    user: JSON.parse(localStorage.getItem('aiops_user') || 'null')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    roles: (state) => state.user?.roles || [],
    nickname: (state) => state.user?.nickname || state.user?.username || '未登录',
    isAdmin: (state) => (state.user?.roles || []).includes('ADMIN')
  },

  actions: {
    async login(username, password) {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = {
        userId: data.userId,
        username: data.username,
        nickname: data.nickname,
        roles: data.roles || []
      }
      localStorage.setItem('aiops_token', this.token)
      localStorage.setItem('aiops_user', JSON.stringify(this.user))
      return data
    },

    async refresh() {
      if (!this.token) return
      try {
        const me = await getMe()
        this.user = {
          userId: me.id,
          username: me.username,
          nickname: me.nickname,
          roles: me.roles || []
        }
        localStorage.setItem('aiops_user', JSON.stringify(this.user))
      } catch (e) {
        // 拦截器已处理跳转
      }
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('aiops_token')
      localStorage.removeItem('aiops_user')
    }
  }
})
