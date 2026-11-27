import http from './http'

/** 同步分析（返回完整结果） */
export const analyze = (eventId) =>
  http.post('/agent/analyze', null, { params: { eventId } })

/** 查询历史分析轨迹 */
export const getTrace = (eventId) => http.get(`/agent/trace/${eventId}`)

/** 查询某事件最近一次分析结果（用于进入页面时回显） */
export const getLastResult = (eventId) => http.get(`/agent/result/${eventId}`)

/** 当前 LLM 实现 */
export const getProvider = () => http.get('/agent/provider')

/**
 * SSE 流式分析。
 *
 * 浏览器原生 EventSource 不支持自定义请求头，无法携带 Authorization，
 * 因此这里用 fetch + ReadableStream 手动解析 SSE 协议。
 *
 * @param {number} eventId 告警事件ID
 * @param {(step: object) => void} onStep 每收到一个步骤回调
 * @param {(result: object) => void} onResult 收到完整结果回调
 * @param {(err: Error) => void} onError 出错回调
 * @returns {AbortController} 可用于中断
 */
export function analyzeStream(eventId, { onStep, onResult, onError } = {}) {
  const controller = new AbortController()
  const token = localStorage.getItem('aiops_token')

  fetch(`/api/agent/analyze/stream?eventId=${eventId}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal: controller.signal
  })
    .then(async (resp) => {
      if (!resp.ok) {
        throw new Error(`SSE 连接失败: HTTP ${resp.status}`)
      }
      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      // 按 SSE 规范逐块解析：以空行分隔事件，字段为 event:/data:/id:
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let sepIndex
        while ((sepIndex = buffer.indexOf('\n\n')) >= 0) {
          const rawEvent = buffer.slice(0, sepIndex)
          buffer = buffer.slice(sepIndex + 2)
          dispatch(rawEvent, onStep, onResult)
        }
      }
      if (buffer.trim()) {
        dispatch(buffer, onStep, onResult)
      }
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError?.(err)
      }
    })

  return controller
}

function dispatch(rawEvent, onStep, onResult) {
  let name = 'message'
  const dataLines = []
  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) name = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return
  const text = dataLines.join('\n')
  if (!text) return

  try {
    const payload = JSON.parse(text)
    if (name === 'step') onStep?.(payload)
    else if (name === 'result') onResult?.(payload)
  } catch (e) {
    // 非 JSON 数据忽略
  }
}
