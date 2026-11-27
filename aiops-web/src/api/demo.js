import http from './http'

/** 注入故障 */
export const injectFault = (mode, intensity = 0.9) =>
  http.post('/demo/fault', null, { params: { mode, intensity } })

/** 当前故障状态 */
export const getFaultState = () => http.get('/demo/fault')

/** 下单（验证故障是否生效） */
export const createOrder = (quantity = 1) =>
  http.post('/demo/orders', null, { params: { quantity } })
