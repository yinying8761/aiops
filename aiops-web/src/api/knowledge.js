import http from './http'

/** 检索模板 */
export const searchKnowledge = (keyword, category, limit = 5) =>
  http.get('/knowledge/search', { params: { keyword, category, limit } })

/** 模板分页 */
export const pageTemplates = (params) => http.get('/knowledge/templates', { params })

/** 模板详情 */
export const getTemplate = (id) => http.get(`/knowledge/templates/${id}`)

/** 新增/更新模板 */
export const saveTemplate = (data) => http.post('/knowledge/templates', data)

/** 删除模板 */
export const deleteTemplate = (id) => http.delete(`/knowledge/templates/${id}`)

/** 分类统计 */
export const getCategories = () => http.get('/knowledge/categories')
