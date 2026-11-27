<template>
  <div>
    <!-- 顶部概览 -->
    <el-row :gutter="14">
      <el-col :span="6" v-for="c in overviewCards" :key="c.label">
        <div class="stat-card">
          <div class="stat-label">{{ c.label }}</div>
          <div class="stat-value">{{ c.value }}<small>{{ c.unit }}</small></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt14">
      <el-col :span="16">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">告警趋势</span>
            <el-radio-group v-model="days" size="small" @change="loadStats">
              <el-radio-button :value="7">近 7 天</el-radio-button>
              <el-radio-button :value="14">近 14 天</el-radio-button>
              <el-radio-button :value="30">近 30 天</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendRef" style="height: 280px"></div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">分类分布</span>
          </div>
          <div ref="categoryRef" style="height: 280px"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt14">
      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">级别分布</span>
          </div>
          <div ref="levelRef" style="height: 260px"></div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">告警最多实例 Top</span>
          </div>
          <div ref="instanceRef" style="height: 260px"></div>
        </div>
      </el-col>

      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">工单状态</span>
          </div>
          <div ref="ticketRef" style="height: 260px"></div>
        </div>
      </el-col>
    </el-row>

    <!-- 故障报告列表 -->
    <div class="card-panel mt14">
      <div class="panel-head">
        <span class="panel-title">故障分析报告</span>
        <el-input
          v-model="reportKeyword"
          placeholder="搜索报告"
          size="small"
          style="width: 220px"
          clearable
          @keyup.enter="loadReports"
          @clear="loadReports"
        />
      </div>

      <el-table :data="reports" v-loading="reportLoading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="title" label="报告标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="rootCause" label="根因结论" min-width="260" show-overflow-tooltip />
        <el-table-column label="耗时" width="90" align="center">
          <template #default="{ row }">{{ row.costSeconds }}s</template>
        </el-table-column>
        <el-table-column label="生成时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showReport(row)">查看</el-button>
            <el-button link type="success" size="small" @click="download(row)">导出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 报告详情 -->
    <el-dialog v-model="reportDialog" title="故障分析报告" width="800px">
      <div v-if="currentReport">
        <h3 class="report-head">{{ currentReport.title }}</h3>
        <div class="report-meta">
          <span>生成时间：{{ fmtTime(currentReport.createTime) }}</span>
          <span>分析耗时：{{ currentReport.costSeconds }} 秒</span>
        </div>
        <div class="report-body" v-html="renderMarkdown(currentReport.reportContent)"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getStats, pageReports, exportReport } from '@/api/report'

const days = ref(7)
const stats = ref({})
const reports = ref([])
const reportLoading = ref(false)
const reportKeyword = ref('')
const reportDialog = ref(false)
const currentReport = ref(null)

const trendRef = ref()
const categoryRef = ref()
const levelRef = ref()
const instanceRef = ref()
const ticketRef = ref()

let charts = {}

const overviewCards = computed(() => [
  { label: '聚合事件数', value: stats.value.aggregatedEvents ?? 0, unit: ' 个' },
  { label: '原始告警数', value: stats.value.rawAlerts ?? 0, unit: ' 条' },
  { label: '聚合降噪率', value: stats.value.dedupRate ?? 0, unit: '%' },
  { label: 'MTTR 平均修复', value: stats.value.mttrMinutes ?? 0, unit: ' 分钟' }
])

async function loadStats() {
  try {
    stats.value = await getStats(days.value)
    await nextTick()
    renderAll()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

function initChart(key, el) {
  if (!el) return null
  if (!charts[key]) charts[key] = echarts.init(el)
  return charts[key]
}

function renderAll() {
  renderTrend()
  renderCategory()
  renderLevel()
  renderInstance()
  renderTicket()
}

function renderTrend() {
  const chart = initChart('trend', trendRef.value)
  if (!chart) return
  const trend = stats.value.trend || []
  chart.setOption({
    grid: { left: 45, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    legend: { data: ['聚合事件', '原始告警'], top: 0, textStyle: { fontSize: 11 } },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.day),
      axisLabel: { fontSize: 11, color: '#8892a8' }
    },
    // 双 Y 轴：左轴给事件数（小量级），右轴给原始告警（大量级）
    yAxis: [
      {
        type: 'value',
        name: '事件数',
        nameTextStyle: { fontSize: 10, color: '#8892a8' },
        minInterval: 1,
        axisLabel: { fontSize: 11, color: '#8892a8' },
        splitLine: { lineStyle: { color: '#f0f3f9' } }
      },
      {
        type: 'value',
        name: '告警数',
        nameTextStyle: { fontSize: 10, color: '#8892a8' },
        axisLabel: { fontSize: 11, color: '#8892a8' },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '聚合事件',
        type: 'bar',
        yAxisIndex: 0,
        data: trend.map((t) => t.eventCount),
        itemStyle: { color: '#2b5fdf', borderRadius: [3, 3, 0, 0] },
        barMaxWidth: 26,
        label: { show: true, position: 'top', fontSize: 10, color: '#2b5fdf' }
      },
      {
        name: '原始告警',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: trend.map((t) => t.alertCount),
        itemStyle: { color: '#e8710a' },
        symbolSize: 5
      }
    ]
  })
}

function renderCategory() {
  const chart = initChart('category', categoryRef.value)
  if (!chart) return
  const data = (stats.value.categoryDistribution || []).map((c) => ({
    name: c.category,
    value: c.cnt
  }))
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    series: [
      {
        type: 'pie',
        radius: ['40%', '66%'],
        data: data.length ? data : [{ name: '暂无数据', value: 1 }],
        label: { fontSize: 11, formatter: '{b}\n{c}' },
        labelLine: { length: 8, length2: 8 }
      }
    ]
  })
}

function renderLevel() {
  const chart = initChart('level', levelRef.value)
  if (!chart) return
  const colors = { P0: '#d93025', P1: '#e8710a', P2: '#1a73e8', P3: '#5f6368' }
  const dist = {}
  ;(stats.value.levelDistribution || []).forEach((r) => {
    dist[r.level] = r.cnt
  })
  chart.setOption({
    grid: { left: 45, right: 20, top: 20, bottom: 28 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['P0', 'P1', 'P2', 'P3'], axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    series: [
      {
        type: 'bar',
        data: ['P0', 'P1', 'P2', 'P3'].map((lv) => ({
          value: dist[lv] || 0,
          itemStyle: { color: colors[lv], borderRadius: [3, 3, 0, 0] }
        })),
        barMaxWidth: 34
      }
    ]
  })
}

function renderInstance() {
  const chart = initChart('instance', instanceRef.value)
  if (!chart) return
  const list = (stats.value.topInstances || []).slice(0, 6).reverse()
  chart.setOption({
    grid: { left: 100, right: 30, top: 20, bottom: 24 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    yAxis: {
      type: 'category',
      data: list.map((i) => i.serviceName),
      axisLabel: { fontSize: 10, width: 90, overflow: 'truncate' }
    },
    series: [
      {
        type: 'bar',
        data: list.map((i) => i.eventCount),
        itemStyle: { color: '#1e8e3e', borderRadius: [0, 3, 3, 0] },
        barMaxWidth: 18
      }
    ]
  })
}

function renderTicket() {
  const chart = initChart('ticket', ticketRef.value)
  if (!chart) return
  const map = {}
  ;(stats.value.ticketStatus || []).forEach((r) => {
    map[r.status] = r.cnt
  })
  const labels = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }
  const colors = { PENDING: '#d93025', PROCESSING: '#1a73e8', RESOLVED: '#1e8e3e', CLOSED: '#5f6368' }
  const keys = ['PENDING', 'PROCESSING', 'RESOLVED', 'CLOSED']
  const total = keys.reduce((s, k) => s + (map[k] || 0), 0)

  // 过滤掉计数为 0 的状态：ECharts 会把 value:0 的项渲染成一条边线，
  // 在图例之外产生视觉杂讯。全为 0 时给一个占位项。
  const seriesData = keys
    .filter((k) => (map[k] || 0) > 0)
    .map((k) => ({
      name: labels[k],
      value: map[k],
      itemStyle: { color: colors[k] }
    }))

  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { fontSize: 11 }, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['42%', '65%'],
        center: ['50%', '44%'],
        data: seriesData.length
          ? seriesData
          : [{ name: '暂无工单', value: 1, itemStyle: { color: '#eef1f7' } }],
        label: { show: false },
        // 关闭最小角度动画残留的边线
        minAngle: 4
      }
    ]
  }, true)
}

async function loadReports() {
  reportLoading.value = true
  try {
    const data = await pageReports({
      pageNum: 1,
      pageSize: 20,
      keyword: reportKeyword.value || undefined
    })
    reports.value = data.records || []
  } finally {
    reportLoading.value = false
  }
}

function showReport(row) {
  currentReport.value = row
  reportDialog.value = true
}

async function download(row) {
  try {
    const resp = await exportReport(row.id)
    const blob = resp.data instanceof Blob ? resp.data : new Blob([resp.data], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `report-${row.id}.md`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('已下载')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function renderMarkdown(md) {
  if (!md) return ''
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return esc(md)
    .replace(/^## (.*)$/gm, '<h4>$1</h4>')
    .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
    .replace(/^- (.*)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>')
    .replace(/\n{2,}/g, '<br/><br/>')
    .replace(/\n/g, '<br/>')
}

function handleResize() {
  Object.values(charts).forEach((c) => c?.resize())
}

onMounted(() => {
  loadStats()
  loadReports()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach((c) => c?.dispose())
  charts = {}
})
</script>

<style scoped>
.mt14 {
  margin-top: 14px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(20, 30, 60, 0.06);
  border-left: 4px solid #2b5fdf;
}

.stat-label {
  font-size: 13px;
  color: #7c88a3;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1f2740;
}

.stat-value small {
  font-size: 13px;
  font-weight: 400;
  color: #7c88a3;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2740;
}

.report-head {
  font-size: 17px;
  margin: 0 0 8px;
  color: #1f2740;
}

.report-meta {
  font-size: 12px;
  color: #8892a8;
  margin-bottom: 16px;
  display: flex;
  gap: 18px;
}

.report-body {
  font-size: 13px;
  line-height: 1.9;
  color: #33405c;
  max-height: 480px;
  overflow-y: auto;
}

.report-body :deep(h4) {
  font-size: 14px;
  color: #1f2740;
  margin: 14px 0 6px;
}

.report-body :deep(ul) {
  padding-left: 18px;
}
</style>
