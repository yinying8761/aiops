<template>
  <div>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="故障演练台"
      description="向被监控的模拟订单系统注入故障，观察「指标恶化 → 告警产生 → 指纹聚合 → Agent 分析 → 自动派单」的完整闭环。建议开启下方自动演示。"
      class="mb14"
    />

    <el-row :gutter="14">
      <!-- 故障注入 -->
      <el-col :span="12">
        <div class="card-panel">
          <div class="panel-title">故障注入</div>
          <div class="sub-hint">选择故障类型，模拟真实的生产故障场景</div>

          <el-radio-group v-model="mode" class="mode-group">
            <el-radio
              v-for="m in modes"
              :key="m.value"
              :value="m.value"
              border
              class="mode-radio"
            >
              <div class="mode-name">{{ m.label }}</div>
              <div class="mode-desc">{{ m.desc }}</div>
            </el-radio>
          </el-radio-group>

          <div class="intensity-row">
            <span>故障强度</span>
            <el-slider v-model="intensity" :min="0" :max="1" :step="0.05" style="flex: 1" />
            <span class="intensity-val">{{ intensity.toFixed(2) }}</span>
          </div>

          <div class="btn-row">
            <el-button type="danger" :loading="injecting" @click="doInject">
              注入故障
            </el-button>
            <el-button type="success" :loading="recovering" @click="doRecover">
              恢复系统
            </el-button>
          </div>
        </div>

        <!-- 当前状态 -->
        <div class="card-panel mt14">
          <div class="panel-title">当前系统状态</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="故障模式">
              <el-tag :type="faultState.faultMode === 'none' ? 'success' : 'danger'" size="small">
                {{ faultState.faultMode }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="健康状态">
              <el-tag :type="health === 'UP' ? 'success' : 'warning'" size="small">
                {{ health }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="强度">{{ faultState.intensity }}</el-descriptions-item>
            <el-descriptions-item label="最近下单">
              {{ orderResult ? orderResult.status : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <el-button
            class="mt14"
            size="small"
            :loading="ordering"
            @click="tryOrder"
          >
            试下单（验证故障是否生效）
          </el-button>
          <div v-if="orderResult" class="order-result">
            <span class="mono">{{ orderResult.orderId }}</span> ·
            {{ orderResult.status }} · {{ orderResult.message }}
          </div>
        </div>
      </el-col>

      <!-- 自动演示 -->
      <el-col :span="12">
        <div class="card-panel">
          <div class="panel-title">一键演示全链路</div>
          <div class="sub-hint">
            自动完成：注入故障 → 推送告警 → 等待聚合 → Agent 分析，并展示实时进度
          </div>

          <el-steps :active="demoStep" direction="vertical" class="demo-steps">
            <el-step title="注入故障" description="向 demo-app 注入连接池耗尽故障" />
            <el-step title="推送告警" description="批量上报 20 条同类告警，触发指纹聚合" />
            <el-step title="等待聚合" description="第 2 条告警达到阈值时创建聚合事件" />
            <el-step title="Agent 分析" description="分类 → 根因 → 处置 → 报告 四阶段流水线" />
            <el-step title="自动派单" description="根据分析结果自动创建工单并派发" />
          </el-steps>

          <el-button
            type="primary"
            style="width: 100%"
            :loading="demoRunning"
            @click="runDemo"
          >
            {{ demoRunning ? '演示进行中...' : '开始一键演示' }}
          </el-button>

          <div v-if="demoLogs.length" class="demo-log">
            <div v-for="(l, i) in demoLogs" :key="i" class="log-item">
              <span class="log-time">{{ l.time }}</span>
              <span :class="['log-text', l.level]">{{ l.text }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { injectFault, getFaultState, createOrder } from '@/api/demo'
import { ingestBatch, pageEvents, rescanPending } from '@/api/alert'
import { analyze } from '@/api/agent'

const router = useRouter()

const modes = [
  { value: 'connection_pool', label: '数据库连接池耗尽', desc: 'conn_pool_usage → 98%' },
  { value: 'cpu', label: 'CPU 打满', desc: 'cpu_usage → 96%' },
  { value: 'memory', label: '内存泄漏', desc: 'mem_usage → 96%' },
  { value: 'network', label: '网络延迟', desc: 'rt_p95 → 3000ms' },
  { value: 'thread_pool', label: '线程池耗尽', desc: 'thread_pool_usage → 96%' }
]

const mode = ref('connection_pool')
const intensity = ref(0.9)
const injecting = ref(false)
const recovering = ref(false)
const ordering = ref(false)
const faultState = ref({ faultMode: 'none', intensity: 0.9 })
const health = ref('UP')
const orderResult = ref(null)

const demoRunning = ref(false)
const demoStep = ref(0)
const demoLogs = ref([])

function log(text, level = '') {
  const now = new Date()
  const time = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`
  demoLogs.value.push({ time, text, level })
}

async function loadState() {
  try {
    const s = await getFaultState()
    faultState.value = s
    health.value = s.faultMode === 'none' ? 'UP' : 'DEGRADED'
  } catch (e) {
    /* ignore */
  }
}

async function doInject() {
  injecting.value = true
  try {
    await injectFault(mode.value, intensity.value)
    ElMessage.success('故障已注入，指标将在下个采集周期恶化')
    await loadState()
  } finally {
    injecting.value = false
  }
}

async function doRecover() {
  recovering.value = true
  try {
    await injectFault('none', 0)
    ElMessage.success('系统已恢复正常')
    await loadState()
  } finally {
    recovering.value = false
  }
}

async function tryOrder() {
  ordering.value = true
  try {
    orderResult.value = await createOrder(1)
  } finally {
    ordering.value = false
  }
}

/**
 * 一键演示：完整跑通业务闭环并展示进度。
 */
async function runDemo() {
  demoRunning.value = true
  demoStep.value = 0
  demoLogs.value = []

  try {
    // 1) 注入故障
    log('开始注入故障：数据库连接池耗尽')
    await injectFault('connection_pool', 0.95)
    demoStep.value = 1
    log('故障已注入，等待指标采集（约需 30 秒可观察到指标恶化）', 'warn')

    // 2) 推送告警
    await sleep(600)
    log('批量推送 20 条同类告警，模拟告警风暴')
    const payload = Array.from({ length: 20 }, (_, i) => ({
      instanceId: 1,
      alertType: 'connection_pool',
      content: `故障演练：数据库连接池耗尽，获取连接超时（第 ${i + 1} 条）`,
      metricValue: 98.0
    }))
    const result = await ingestBatch(payload)
    demoStep.value = 2
    log(
      `接入完成：共 ${result.total} 条告警，创建 ${result.eventCreated} 个聚合事件，` +
        `去重率 ${result.dedupRate}%`
    )

    // 3) 定位事件
    await sleep(800)
    const events = await pageEvents({ pageNum: 1, pageSize: 5 })
    const event = (events.records || [])[0]
    if (!event) {
      log('未找到聚合事件，请检查服务状态', 'error')
      demoRunning.value = false
      return
    }
    demoStep.value = 3
    log(`聚合事件已生成：ID=${event.id}，级别=${event.level}，聚合条数=${event.count}`)

    // 4) Agent 分析
    log('触发 Agent 四阶段分析（分类 → 根因 → 处置 → 报告）')
    const res = await analyze(event.id)
    demoStep.value = 4
    log(`分类：${res.classification?.category} / ${res.classification?.level}`)
    log(`根因：${res.rca?.rootCause}`)
    log(`工具调用 ${res.rca?.toolTrace?.length || 0} 次：${(res.rca?.toolTrace || []).join('、')}`)
    log(`处置方案 ${res.remediation?.steps?.length || 0} 步，风险 ${res.remediation?.risk}`)

    // 5) 派单
    demoStep.value = 5
    if (res.ticketNo) {
      log(`已自动创建工单 ${res.ticketNo}`, 'success')
    } else {
      log('工单创建失败，请查看服务日志', 'error')
    }
    log(`分析总耗时 ${res.costMs} ms`, 'success')
    log('演示完成！可打开「告警中心」查看事件，或进入分析页回放执行过程', 'success')

    // 恢复系统
    await injectFault('none', 0)
    await loadState()
    log('已自动恢复系统正常状态')
  } catch (e) {
    log(`演示失败：${e.message || e}`, 'error')
  } finally {
    demoRunning.value = false
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

onMounted(loadState)
</script>

<style scoped>
.mb14 {
  margin-bottom: 14px;
}

.mt14 {
  margin-top: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2740;
  margin-bottom: 6px;
}

.sub-hint {
  font-size: 12px;
  color: #98a2b8;
  margin-bottom: 16px;
}

.mode-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.mode-radio {
  width: 100%;
  height: auto;
  margin-right: 0;
  padding: 10px 14px;
  align-items: flex-start;
}

.mode-radio :deep(.el-radio__label) {
  width: 100%;
}

.mode-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2740;
}

.mode-desc {
  font-size: 12px;
  color: #98a2b8;
  margin-top: 2px;
}

.intensity-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 18px 0 6px;
  font-size: 13px;
  color: #6b7a99;
}

.intensity-val {
  font-family: Consolas, monospace;
  color: #2b5fdf;
  font-weight: 600;
  width: 40px;
  text-align: right;
}

.btn-row {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.order-result {
  margin-top: 10px;
  font-size: 12px;
  color: #6b7a99;
  background: #f7f9fd;
  padding: 8px 10px;
  border-radius: 5px;
}

.demo-steps {
  margin: 10px 0 18px;
}

.demo-log {
  margin-top: 16px;
  max-height: 260px;
  overflow-y: auto;
  background: #131a2b;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}

.log-item {
  display: flex;
  gap: 10px;
  line-height: 1.9;
}

.log-time {
  color: #5c6b88;
  flex-shrink: 0;
}

.log-text {
  color: #b8c4dd;
  word-break: break-all;
}

.log-text.warn {
  color: #e8a33a;
}

.log-text.error {
  color: #e05c50;
}

.log-text.success {
  color: #4ade80;
}
</style>
