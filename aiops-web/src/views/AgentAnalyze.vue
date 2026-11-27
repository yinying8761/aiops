<template>
  <div class="analyze-page">
    <!-- 事件头部信息 -->
    <div class="card-panel head-panel">
      <div class="head-left">
        <el-button :icon="ArrowLeft" link @click="$router.push('/alerts')">返回告警中心</el-button>
        <div class="event-title">
          <span v-if="event" class="level-tag" :class="`level-${event.level}`">
            {{ event.level }}
          </span>
          <span>{{ event?.title || '加载中...' }}</span>
        </div>
        <div class="event-meta" v-if="event">
          <span>事件 #{{ event.id }}</span>
          <el-divider direction="vertical" />
          <span>聚合 {{ event.count }} 条告警</span>
          <el-divider direction="vertical" />
          <span>类型 {{ event.alertType }}</span>
          <el-divider direction="vertical" />
          <span>首次 {{ fmtTime(event.firstTime) }}</span>
        </div>
      </div>
      <div class="head-right">
        <el-tag v-if="provider" :type="provider === 'mock' ? 'warning' : 'success'" size="small">
          {{ provider === 'mock' ? '模拟模型（规则+模板）' : 'DeepSeek 真实模型' }}
        </el-tag>
        <el-button
          type="primary"
          :loading="running"
          :icon="VideoPlay"
          @click="startAnalyze"
        >
          {{ running ? '分析中...' : hasResult ? '重新分析' : '开始 AI 分析' }}
        </el-button>
      </div>
    </div>

    <el-row :gutter="14" class="mt14">
      <!-- 左侧：执行过程 -->
      <el-col :span="14">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">
              执行过程
              <el-tag v-if="steps.length" size="small" effect="plain" style="margin-left: 8px">
                {{ steps.length }} 步
              </el-tag>
            </span>
            <span class="elapsed" v-if="elapsed">耗时 {{ elapsed }} ms</span>
          </div>

          <div class="steps-container" ref="stepsRef">
            <el-empty
              v-if="!steps.length && !running"
              description="点击右上角「开始 AI 分析」，观察 Agent 如何调用工具定位根因"
            />
            <div
              v-for="(s, i) in steps"
              :key="i"
              class="step-item"
              :class="s.type"
            >
              <div class="step-meta">
                <el-tag size="small" effect="plain">{{ s.stageLabel }}</el-tag>
                <span class="step-type">{{ typeLabel(s.type) }}</span>
                <span v-if="s.toolName" class="tool-name mono">{{ s.toolName }}</span>
                <span v-if="s.costMs != null" class="cost">{{ s.costMs }} ms</span>
              </div>
              <div class="step-content">{{ truncate(s.content, 320) }}</div>
            </div>
            <div v-if="running" class="thinking">
              <el-icon class="is-loading"><Loading /></el-icon>
              Agent 正在推理...
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧：结论 -->
      <el-col :span="10">
        <!-- 分类与定级 -->
        <div class="card-panel">
          <div class="panel-title">分类与定级</div>
          <div v-if="result?.classification" class="result-block">
            <div class="kv">
              <span>分类</span>
              <el-tag type="primary" effect="dark">{{ result.classification.category }}</el-tag>
            </div>
            <div class="kv">
              <span>级别</span>
              <span class="level-tag" :class="`level-${result.classification.level}`">
                {{ result.classification.level }}
              </span>
            </div>
            <div class="summary">{{ result.classification.summary }}</div>
          </div>
          <el-empty v-else description="待分析" :image-size="60" />
        </div>

        <!-- 根因结论 -->
        <div class="card-panel mt14">
          <div class="panel-title">根因结论</div>
          <div v-if="result?.rca" class="result-block">
            <div class="root-cause">{{ result.rca.rootCause }}</div>
            <div class="kv" v-if="result.rca.confidence != null">
              <span>置信度</span>
              <el-progress
                :percentage="Math.round(result.rca.confidence * 100)"
                :stroke-width="10"
                style="width: 140px"
              />
            </div>
            <div class="kv" v-if="result.rca.impact">
              <span>影响面</span>
              <span class="small-text">{{ result.rca.impact }}</span>
            </div>

            <div class="sub-title">证据链</div>
            <ul class="evidence">
              <li v-for="(e, i) in result.rca.evidence || []" :key="i">{{ e }}</li>
            </ul>

            <div class="sub-title" v-if="result.rca.toolTrace?.length">
              工具调用（{{ result.rca.toolTrace.length }} 次）
            </div>
            <div class="tool-trace">
              <el-tooltip
                v-for="(t, i) in result.rca.toolTrace || []"
                :key="i"
                :content="t"
                placement="top"
              >
                <span class="tool-chip mono">{{ shortTool(t) }}</span>
              </el-tooltip>
            </div>
          </div>
          <el-empty v-else description="待分析" :image-size="60" />
        </div>

        <!-- 工单 -->
        <div class="card-panel mt14" v-if="result?.ticketNo">
          <div class="panel-title">自动派单</div>
          <div class="ticket-box">
            <div class="kv">
              <span>工单号</span>
              <b class="mono">{{ result.ticketNo }}</b>
            </div>
            <el-button type="primary" link @click="goTicket">查看工单详情 →</el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 处置方案与报告 -->
    <el-row :gutter="14" class="mt14" v-if="result?.remediation">
      <el-col :span="12">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">处置建议</span>
            <el-tag
              :type="riskType(result.remediation.risk)"
              size="small"
              effect="plain"
            >
              风险 {{ result.remediation.risk }}
            </el-tag>
          </div>
          <el-steps direction="vertical" :active="stepsDone" finish-status="success">
            <el-step
              v-for="(s, i) in result.remediation.steps"
              :key="i"
              :title="`步骤 ${i + 1}`"
              :description="s"
            />
          </el-steps>
          <div class="suggestion" v-if="result.remediation.suggestion">
            {{ result.remediation.suggestion }}
          </div>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">故障分析报告</span>
          </div>
          <div class="report-title">{{ result.report?.title }}</div>
          <div class="report-content" v-html="renderMarkdown(result.report?.content)"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, VideoPlay, Loading } from '@element-plus/icons-vue'
import { getEvent } from '@/api/alert'
import { analyze, analyzeStream, getProvider, getLastResult, getTrace } from '@/api/agent'

const route = useRoute()
const router = useRouter()

const eventId = Number(route.params.id)
const event = ref(null)
const provider = ref('')
const steps = ref([])
const result = ref(null)
const running = ref(false)
const elapsed = ref(0)
const stepsRef = ref()
let controller = null

const hasResult = computed(() => !!result.value)
const stepsDone = computed(() => result.value?.remediation?.steps?.length || 0)

async function loadEvent() {
  try {
    event.value = await getEvent(eventId)
  } catch (e) {
    ElMessage.error('加载事件失败')
  }
}

function startAnalyze() {
  if (running.value) return
  steps.value = []
  result.value = null
  running.value = true
  elapsed.value = 0
  const start = Date.now()

  controller = analyzeStream(eventId, {
    onStep: (step) => {
      steps.value.push(step)
      nextTick(scrollToBottom)
    },
    onResult: (res) => {
      result.value = res
      elapsed.value = res.costMs || Date.now() - start
      running.value = false
      ElMessage.success('分析完成')
      loadEvent()
    },
    onError: (err) => {
      running.value = false
      ElMessage.error(err.message || '分析失败')
    }
  })
}

function scrollToBottom() {
  const el = stepsRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function goTicket() {
  if (result.value?.ticketId) {
    router.push({ path: '/tickets', query: { highlight: result.value.ticketId } })
  }
}

function typeLabel(t) {
  return {
    STAGE_START: '阶段开始',
    STAGE_END: '阶段完成',
    THOUGHT: '推理',
    TOOL_CALL: '工具调用',
    TOOL_RESULT: '工具返回',
    OUTPUT: '输出',
    DONE: '完成'
  }[t] || t
}

function riskType(r) {
  return { 低: 'success', 中: 'warning', 高: 'danger' }[r] || 'info'
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 截断过长的步骤内容，避免一屏被单条 JSON 占满 */
function truncate(text, max) {
  if (!text) return ''
  return text.length <= max ? text : text.slice(0, max) + ' …（已截断）'
}

/** 工具调用标签只显示「工具名(简短参数)」，保证一行放得下 */
function shortTool(t) {
  if (!t) return ''
  const lb = t.indexOf('(')
  const rb = t.lastIndexOf(')')
  if (lb < 0) return t
  const name = t.slice(0, lb)
  let args = t.slice(lb + 1, rb)
  if (args.length > 28) args = args.slice(0, 28) + '…'
  return `${name}(${args})`
}

/** 极简 Markdown 渲染：仅支持标题、列表、加粗、换行 */
function renderMarkdown(md) {
  if (!md) return ''
  const esc = (s) =>
    s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc(md)
    .replace(/^### (.*)$/gm, '<h4>$1</h4>')
    .replace(/^## (.*)$/gm, '<h3>$1</h3>')
    .replace(/^# (.*)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
    .replace(/\n{2,}/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}

onMounted(async () => {
  await loadEvent()
  try {
    const p = await getProvider()
    provider.value = p.provider
  } catch (e) {
    /* ignore */
  }
  // 回显上次分析结果，避免用户每次进页面都要重跑一遍
  await loadHistory()
})

/** 加载该事件最近一次的分析结果与过程轨迹 */
async function loadHistory() {
  try {
    const [res, trace] = await Promise.all([
      getLastResult(eventId),
      getTrace(eventId)
    ])
    if (res) {
      result.value = res
      elapsed.value = res.costMs || 0
    }
    if (trace?.length) {
      steps.value = trace
    }
  } catch (e) {
    // 无历史结果属正常情况，不提示
  }
}

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<style scoped>
.head-panel {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.event-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 600;
  color: #1f2740;
  margin-top: 8px;
}

.event-meta {
  font-size: 12px;
  color: #8892a8;
  margin-top: 8px;
  display: flex;
  align-items: center;
}

.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.mt14 {
  margin-top: 14px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2740;
}

.elapsed {
  font-size: 12px;
  color: #8892a8;
}

.steps-container {
  max-height: 620px;
  overflow-y: auto;
  padding-right: 4px;
}

.step-type {
  font-size: 12px;
  color: #445072;
  font-weight: 500;
}

.tool-name {
  font-size: 11px;
  background: #fff3e6;
  color: #b35c00;
  padding: 1px 6px;
  border-radius: 3px;
}

.cost {
  font-size: 11px;
  color: #98a2b8;
  margin-left: auto;
}

.step-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  color: #2b5fdf;
  font-size: 13px;
}

.result-block {
  font-size: 13px;
}

.kv {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 0;
  border-bottom: 1px dashed #eef1f7;
  color: #6b7a99;
}

.kv > span:first-child {
  flex-shrink: 0;
}

.summary {
  margin-top: 10px;
  color: #33405c;
  line-height: 1.7;
}

.root-cause {
  font-size: 14px;
  color: #1f2740;
  line-height: 1.7;
  background: #f2f6ff;
  border-left: 3px solid #2b5fdf;
  padding: 10px 12px;
  border-radius: 0 6px 6px 0;
  margin-bottom: 10px;
}

.sub-title {
  font-size: 13px;
  font-weight: 600;
  color: #445072;
  margin: 12px 0 6px;
}

.evidence {
  margin: 0;
  padding-left: 18px;
  color: #33405c;
  font-size: 13px;
  line-height: 1.9;
}

.tool-trace {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-chip {
  display: inline-block;
  max-width: 100%;
  padding: 2px 8px;
  border-radius: 10px;
  background: #fff3e6;
  color: #b35c00;
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: default;
}

.small-text {
  font-size: 12px;
  text-align: right;
  color: #33405c;
  max-width: 62%;
}

.ticket-box {
  font-size: 13px;
}

.suggestion {
  margin-top: 14px;
  padding: 10px 12px;
  background: #f7f9fd;
  border-radius: 6px;
  font-size: 13px;
  color: #4a5670;
  line-height: 1.7;
}

.report-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2740;
  margin-bottom: 12px;
}

.report-content {
  font-size: 13px;
  line-height: 1.9;
  color: #33405c;
  max-height: 460px;
  overflow-y: auto;
}

.report-content :deep(h2),
.report-content :deep(h3),
.report-content :deep(h4) {
  font-size: 14px;
  color: #1f2740;
  margin: 12px 0 6px;
}

.report-content :deep(ul) {
  padding-left: 18px;
}
</style>
