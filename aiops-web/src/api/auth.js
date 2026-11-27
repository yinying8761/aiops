import http from './http'

/** 登录 */
export const login = (data) => http.post('/auth/login', data)

/** 当前用户 */
export const getMe = () => http.get('/auth/me')

/** 用户分页 */
export const pageUsers = (params) => http.get('/auth/users', { params })

/** 新增/更新用户 */
export const saveUser = (data) => http.post('/auth/users', data)

/** 删除用户 */
export const deleteUser = (id) => http.delete(`/auth/users/${id}`)

/** 角色列表 */
export const listRoles = () => http.get('/auth/roles')
