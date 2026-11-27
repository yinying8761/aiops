import http from './http'

/** 指标时序 */
export const getSeries = (instanceId, metricName, range = '30m') =>
  http.get(`/metrics/${instanceId}/series`, { params: { metricName, range } })

/** 最新指标快照 */
export const getLatestMetrics = (instanceId) =>
  http.get(`/metrics/${instanceId}/latest`)

/** 最近日志 */
export const getLogs = (instanceId, range = '30m', limit = 50) =>
  http.get(`/metrics/${instanceId}/logs`, { params: { range, limit } })

/** 手动采集 */
export const collectMetrics = (instanceId) =>
  http.post('/metrics/collect', null, { params: instanceId ? { instanceId } : {} })
