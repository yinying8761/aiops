import http from './http'

/** 综合统计 */
export const getStats = (days = 7) => http.get('/reports/stats', { params: { days } })

/** 概览指标 */
export const getOverview = () => http.get('/reports/overview')

/** 报告列表 */
export const pageReports = (params) => http.get('/reports/list', { params })

/** 报告详情 */
export const getReport = (id) => http.get(`/reports/${id}`)

/** 导出 Markdown（返回 Blob） */
export const exportReport = (id) =>
  http.get(`/reports/${id}/export`, { responseType: 'blob' })
