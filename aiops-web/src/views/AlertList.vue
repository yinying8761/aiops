<template>
  <div>
    <div class="card-panel">
      <!-- 筛选条 -->
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="级别">
          <el-select v-model="query.level" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="l in levels" :key="l.code" :label="l.label" :value="l.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statuses" :key="s.code" :label="s.label" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="告警标题 / 类型"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
          <el-button
            type="warning"
            :icon="RefreshRight"
            :loading="rescanning"
            @click="handleRescan"
          >
            补偿投递
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" size="default" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <span class="level-tag" :class="`level-${row.level}`">{{ row.level }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" effect="plain">{{ row.category }}</el-tag>
            <span v-else class="text-muted">待分析</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="告警事件" min-width="240" show-overflow-tooltip />
        <el-table-column prop="alertType" label="类型" width="150" show-overflow-tooltip />
        <el-table-column label="聚合" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="danger" effect="dark">{{ row.count }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="首次发生" width="160">
          <template #default="{ row }">{{ fmtTime(row.firstTime) }}</template>
        </el-table-column>
        <el-table-column label="分析状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="analyzeTagType(row.analyzeStatus)" size="small">
              {{ analyzeLabel(row.analyzeStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="事件状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="plain">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="goAnalyze(row)">
              AI 分析
            </el-button>
            <el-button link type="info" size="small" @click="showDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="!['RESOLVED','CLOSED','IGNORED'].includes(row.status)"
              link
              type="success"
              size="small"
              @click="resolve(row)"
            >
              标记解决
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="告警事件详情" size="640px">
      <div v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="事件ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="级别">
            <span class="level-tag" :class="`level-${current.level}`">{{ current.level }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="分类">{{ current.category || '待分析' }}</el-descriptions-item>
          <el-descriptions-item label="聚合条数">{{ current.count }}</el-descriptions-item>
          <el-descriptions-item label="首次发生">{{ fmtTime(current.firstTime) }}</el-descriptions-item>
          <el-descriptions-item label="最近发生">{{ fmtTime(current.lastTime) }}</el-descriptions-item>
          <el-descriptions-item label="持续时间">
            {{ current.durationSeconds }} 秒
          </el-descriptions-item>
          <el-descriptions-item label="指纹">
            <span class="mono" style="font-size: 11px">{{ current.fingerprint }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">
            {{ current.summary || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">原始告警明细（最近 20 条）</h4>
        <el-table :data="alerts" size="small" border max-height="300">
          <el-table-column prop="createTime" label="时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="level" label="级别" width="70" />
          <el-table-column prop="content" label="内容" show-overflow-tooltip />
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, RefreshRight } from '@element-plus/icons-vue'
import {
  pageEvents,
  listEventAlerts,
  updateEventStatus,
  rescanPending
} from '@/api/alert'

const router = useRouter()

const loading = ref(false)
const rescanning = ref(false)
const rows = ref([])
const total = ref(0)
const alerts = ref([])
const detailVisible = ref(false)
const current = ref(null)

const query = ref({
  pageNum: 1,
  pageSize: 10,
  level: '',
  status: '',
  keyword: ''
})

const levels = [
  { code: 'P0', label: 'P0 紧急' },
  { code: 'P1', label: 'P1 严重' },
  { code: 'P2', label: 'P2 一般' },
  { code: 'P3', label: 'P3 提示' }
]

const statuses = [
  { code: 'NEW', label: '待处理' },
  { code: 'ACK', label: '已确认' },
  { code: 'ANALYZING', label: '分析中' },
  { code: 'HANDLING', label: '处置中' },
  { code: 'RESOLVED', label: '已解决' },
  { code: 'CLOSED', label: '已关闭' },
  { code: 'IGNORED', label: '已忽略' }
]

async function load() {
  loading.value = true
  try {
    const data = await pageEvents({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      level: query.value.level || undefined,
      status: query.value.status || undefined,
      keyword: query.value.keyword || undefined
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: 10, level: '', status: '', keyword: '' }
  load()
}

async function handleRescan() {
  rescanning.value = true
  try {
    const r = await rescanPending(50)
    ElMessage.success(`已重新投递 ${r.count} 个待分析事件`)
    load()
  } finally {
    rescanning.value = false
  }
}

async function showDetail(row) {
  current.value = row
  detailVisible.value = true
  alerts.value = (await listEventAlerts(row.id)) || []
}

async function resolve(row) {
  await ElMessageBox.confirm(`确定将事件 #${row.id} 标记为已解决？`, '提示', {
    type: 'warning'
  })
  await updateEventStatus(row.id, 'RESOLVED')
  ElMessage.success('已标记为已解决')
  load()
}

function goAnalyze(row) {
  router.push(`/alerts/${row.id}/analyze`)
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

function statusLabel(s) {
  return statuses.find((x) => x.code === s)?.label || s
}

function statusTagType(s) {
  return {
    NEW: 'danger',
    ACK: 'warning',
    ANALYZING: 'warning',
    HANDLING: 'primary',
    RESOLVED: 'success',
    CLOSED: 'info',
    IGNORED: 'info'
  }[s] || 'info'
}

function analyzeLabel(s) {
  return {
    PENDING: '待分析',
    RUNNING: '分析中',
    DONE: '已完成',
    FAILED: '失败'
  }[s] || s
}

function analyzeTagType(s) {
  return {
    PENDING: 'info',
    RUNNING: 'warning',
    DONE: 'success',
    FAILED: 'danger'
  }[s] || 'info'
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  margin-bottom: 6px;
}

.text-muted {
  color: #98a2b8;
  font-size: 12px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.section-title {
  font-size: 14px;
  margin: 20px 0 10px;
  color: #1f2740;
}
</style>
