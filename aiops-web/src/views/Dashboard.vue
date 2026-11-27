<template>
  <div class="dashboard">
    <!-- 顶部指标卡 -->
    <el-row :gutter="14">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <div class="metric-card" :class="card.tone">
          <div class="metric-label">{{ card.label }}</div>
          <div class="metric-value">{{ card.value }}<small>{{ card.unit }}</small></div>
          <div class="metric-hint">{{ card.hint }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt14">
      <!-- 24 小时趋势 -->
      <el-col :span="16">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">近 24 小时告警趋势</span>
            <el-button link type="primary" @click="loadDashboard">刷新</el-button>
          </div>
          <div ref="trendRef" class="chart" style="height: 260px"></div>
        </div>
      </el-col>

      <!-- 级别分布 -->
      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">告警级别分布（未关闭）</span>
          </div>
          <div ref="levelRef" class="chart" style="height: 260px"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="14" class="mt14">
      <!-- 实时事件流 -->
      <el-col :span="16">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">实时告警事件</span>
            <el-radio-group v-model="autoRefresh" size="small">
              <el-radio-button :value="true">自动刷新</el-radio-button>
              <el-radio-button :value="false">手动</el-radio-button>
            </el-radio-group>
          </div>

          <el-table :data="recentEvents" size="small" :show-header="true" height="300">
            <el-table-column label="级别" width="72" align="center">
              <template #default="{ row }">
                <span class="level-tag" :class="`level-${row.level}`">{{ row.level }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="告警事件" min-width="260" show-overflow-tooltip />
            <el-table-column prop="alertType" label="类型" width="118" show-overflow-tooltip />
            <el-table-column label="聚合数" width="100" align="center">
              <template #default="{ row }">
                <span class="count-badge">{{ row.count }} 条</span>
              </template>
            </el-table-column>
            <el-table-column label="最近发生" width="140">
              <template #default="{ row }">{{ fmtTime(row.lastTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="goAnalyze(row)">
                  分析
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 降噪效果 -->
      <el-col :span="8">
        <div class="card-panel">
          <div class="panel-head">
            <span class="panel-title">聚合降噪效果（24h）</span>
          </div>
          <div class="dedup-box">
            <!--
              用 ECharts 仪表盘而非 el-progress：
              el-progress 的 dashboard 类型在部分环境下圆环不渲染，且无法显示自定义颜色分区。
            -->
            <div ref="dedupRef" class="dedup-gauge"></div>
            <div class="dedup-detail">
              <div class="row"><span>原始告警</span><b>{{ aggregatedAlerts }}</b></div>
              <div class="row"><span>聚合事件</span><b>{{ events }}</b></div>
              <div class="row"><span>减少调用</span><b class="good">{{ aggregatedAlerts - events }}</b></div>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getDashboard } from '@/api/alert'

const router = useRouter()

const trendRef = ref()
const levelRef = ref()
const dedupRef = ref()
const autoRefresh = ref(true)

const stats = ref({
  todayEvents: 0,
  todayAlerts: 0,
  pendingEvents: 0,
  dedupRate: 0,
  aggregatedAlerts24h: 0,
  events24h: 0,
  levelDistribution: {},
  hourlyTrend: [],
  recentEvents: []
})

let trendChart = null
let levelChart = null
let dedupChart = null
let timer = null

const recentEvents = computed(() => stats.value.recentEvents || [])
const aggregatedAlerts = computed(() => stats.value.aggregatedAlerts24h || 0)
const events = computed(() => stats.value.events24h || 0)
const dedupRate = computed(() => Math.round(stats.value.dedupRate || 0))

const progressColor = computed(() => {
  const r = dedupRate.value
  if (r >= 90) return '#1e8e3e'
  if (r >= 70) return '#e8710a'
  return '#d93025'
})

/** 渲染降噪率仪表盘 */
function renderDedup() {
  if (!dedupRef.value) return
  if (!dedupChart) dedupChart = echarts.init(dedupRef.value)

  dedupChart.setOption({
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 100,
        radius: '92%',
        center: ['50%', '56%'],
        progress: {
          show: true,
          width: 14,
          roundCap: true,
          itemStyle: { color: progressColor.value }
        },
        axisLine: {
          lineStyle: {
            width: 14,
            color: [[1, '#eef1f7']]
          }
        },
        pointer: { show: false },
        axisTick: { show: false },
        splitLine: { show: false },
        axisLabel: { show: false },
        anchor: { show: false },
        title: {
          show: true,
          offsetCenter: [0, '32%'],
          fontSize: 12,
          color: '#8892a8'
        },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '2%'],
          fontSize: 26,
          fontWeight: 'bold',
          color: '#1f2740',
          formatter: '{value}%'
        },
        data: [{ value: dedupRate.value, name: '降噪率' }]
      }
    ]
  })
}

const cards = computed(() => [
  {
    label: '今日告警总数',
    value: stats.value.todayAlerts,
    unit: ' 条',
    hint: `聚合为 ${stats.value.todayEvents} 个事件`,
    tone: 'blue'
  },
  {
    label: '今日聚合事件',
    value: stats.value.todayEvents,
    unit: ' 个',
    hint: '同类告警已自动去重',
    tone: 'orange'
  },
  {
    label: '待处理事件',
    value: stats.value.pendingEvents,
    unit: ' 个',
    hint: '需关注 P0/P1',
    tone: 'red'
  },
  {
    label: '聚合降噪率',
    value: dedupRate.value,
    unit: '%',
    hint: '大幅减少下游分析调用',
    tone: 'green'
  }
])

async function loadDashboard() {
  try {
    const data = await getDashboard()
    stats.value = data
    await nextTick()
    renderTrend()
    renderLevel()
    renderDedup()
  } catch (e) {
    // 拦截器已提示
  }
}

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)

  const trend = stats.value.hourlyTrend || []
  trendChart.setOption({
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: trend.map((t) => t.time),
      axisLabel: { fontSize: 11, color: '#8892a8' },
      axisLine: { lineStyle: { color: '#e0e5ef' } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { fontSize: 11, color: '#8892a8' },
      splitLine: { lineStyle: { color: '#f0f3f9' } }
    },
    series: [
      {
        name: '告警事件',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        data: trend.map((t) => t.count),
        itemStyle: { color: '#2b5fdf' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(43,95,223,0.28)' },
            { offset: 1, color: 'rgba(43,95,223,0.02)' }
          ])
        }
      }
    ]
  })
}

function renderLevel() {
  if (!levelRef.value) return
  if (!levelChart) levelChart = echarts.init(levelRef.value)

  const dist = stats.value.levelDistribution || {}
  const colors = { P0: '#d93025', P1: '#e8710a', P2: '#1a73e8', P3: '#5f6368' }
  const data = ['P0', 'P1', 'P2', 'P3'].map((lv) => ({
    name: lv,
    value: dist[lv] || 0,
    itemStyle: { color: colors[lv] }
  }))

  levelChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 个 ({d}%)' },
    legend: {
      bottom: 0,
      icon: 'circle',
      textStyle: { fontSize: 11, color: '#6b7a99' }
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '68%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        label: { show: true, formatter: '{b}\n{c}' },
        labelLine: { length: 8, length2: 8 },
        data
      }
    ]
  })
}

function goAnalyze(row) {
  router.push(`/alerts/${row.id}/analyze`)
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function handleResize() {
  trendChart?.resize()
  levelChart?.resize()
  dedupChart?.resize()
}

onMounted(() => {
  loadDashboard()
  timer = setInterval(() => {
    if (autoRefresh.value) loadDashboard()
  }, 10000)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  levelChart?.dispose()
  dedupChart?.dispose()
})
</script>

<style scoped>
.mt14 {
  margin-top: 14px;
}

.metric-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(20, 30, 60, 0.06);
  border-left: 4px solid #2b5fdf;
}

.metric-card.orange { border-left-color: #e8710a; }
.metric-card.red { border-left-color: #d93025; }
.metric-card.green { border-left-color: #1e8e3e; }

.metric-label {
  font-size: 13px;
  color: #7c88a3;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2740;
  line-height: 1.1;
}

.metric-value small {
  font-size: 13px;
  font-weight: 400;
  color: #7c88a3;
  margin-left: 2px;
}

.metric-hint {
  font-size: 12px;
  color: #98a2b8;
  margin-top: 6px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2740;
}

.chart {
  width: 100%;
}

.dedup-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 6px;
}

.dedup-num {
  font-size: 22px;
  font-weight: 700;
  color: #1f2740;
}

.dedup-label {
  font-size: 12px;
  color: #8892a8;
}

.dedup-gauge {
  width: 100%;
  height: 190px;
}

/* 聚合数徽标：不用 el-tag，避免小尺寸下被列宽挤压成细条 */
.count-badge {
  display: inline-block;
  min-width: 46px;
  padding: 1px 8px;
  border-radius: 10px;
  background: #fdecea;
  color: #c5221f;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.dedup-detail {
  width: 100%;
  margin-top: 14px;
  font-size: 13px;
}

.dedup-detail .row {
  display: flex;
  justify-content: space-between;
  padding: 6px 4px;
  border-bottom: 1px dashed #eef1f7;
  color: #6b7a99;
}

.dedup-detail .row b {
  color: #1f2740;
}

.dedup-detail .row b.good {
  color: #1e8e3e;
}
</style>
