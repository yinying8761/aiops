import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

/**
 * 统一 Axios 实例。
 * 所有请求走 /api 前缀，由 Vite 代理转发到网关，网关负责 JWT 鉴权。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

// 请求拦截：自动带上 Token
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('aiops_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截：统一处理 {code, message, data}
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 文件下载等非 JSON 响应直接返回
    if (body instanceof Blob) {
      return response
    }
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      localStorage.removeItem('aiops_token')
      localStorage.removeItem('aiops_user')
      ElMessage.error(body?.message || '登录已过期，请重新登录')
      router.push('/login')
    } else {
      ElMessage.error(body?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default http
