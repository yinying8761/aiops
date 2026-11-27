import http from './http'

/** 聚合告警事件分页 */
export const pageEvents = (params) => http.get('/alerts/events', { params })

/** 事件详情 */
export const getEvent = (id) => http.get(`/alerts/events/${id}`)

/** 事件下的原始告警 */
export const listEventAlerts = (id) => http.get(`/alerts/events/${id}/alerts`)

/** 更新事件状态 */
export const updateEventStatus = (id, status) =>
  http.put(`/alerts/events/${id}/status`, null, { params: { status } })

/** 大屏概览 */
export const getDashboard = () => http.get('/alerts/dashboard')

/** 单条告警上报 */
export const ingestAlert = (data) => http.post('/alerts', data)

/** 批量告警上报（告警风暴演示） */
export const ingestBatch = (list) => http.post('/alerts/batch', list)

/** 补偿扫描 */
export const rescanPending = (limit = 50) =>
  http.post('/alerts/events/rescan', null, { params: { limit } })
