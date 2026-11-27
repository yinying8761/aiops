import http from './http'

/** 工单分页 */
export const pageTickets = (params) => http.get('/tickets', { params })

/** 工单详情 */
export const getTicket = (id) => http.get(`/tickets/${id}`)

/** 工单流转历史 */
export const getTicketLogs = (id) => http.get(`/tickets/${id}/logs`)

/** 状态流转 */
export const transitTicket = (id, status, remark) =>
  http.put(`/tickets/${id}/status`, null, { params: { status, remark } })

/** 按事件查工单 */
export const getTicketByEvent = (eventId) => http.get(`/tickets/by-event/${eventId}`)

/** 工单看板 */
export const getTicketBoard = () => http.get('/tickets/board')
