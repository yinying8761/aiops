<template>
  <div>
    <div class="card-panel">
      <el-form :inline="true" class="filter-bar">
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="s in statuses" :key="s.code" :label="s.label" :value="s.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" placeholder="全部" clearable style="width: 110px">
            <el-option v-for="p in ['P0', 'P1', 'P2', 'P3']" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="工单号 / 标题"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe size="default">
        <el-table-column prop="ticketNo" label="工单号" width="160">
          <template #default="{ row }">
            <span class="mono">{{ row.ticketNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">
            <span class="level-tag" :class="`level-${row.priority}`">{{ row.priority }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="assignee" label="处理人" width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">
              详情
            </el-button>
            <el-button
              v-for="action in nextActions(row.status)"
              :key="action.status"
              link
              :type="action.type"
              size="small"
              @click="transit(row, action)"
            >
              {{ action.label }}
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
    <el-drawer v-model="drawerVisible" title="工单详情" size="680px">
      <div v-if="current">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单号">
            <span class="mono">{{ current.ticketNo }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="优先级">
            <span class="level-tag" :class="`level-${current.priority}`">
              {{ current.priority }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(current.status)" size="small">
              {{ statusLabel(current.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="处理人">{{ current.assignee }}</el-descriptions-item>
          <el-descriptions-item label="关联事件">
            <el-button link type="primary" @click="goEvent(current.eventId)">
              #{{ current.eventId }}
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ current.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(current.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="关闭时间">{{ fmtTime(current.closeTime) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ current.title }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">Agent 处置建议</h4>
        <pre class="suggestion-pre">{{ current.suggestion || '（无）' }}</pre>

        <template v-if="current.solution">
          <h4 class="section-title">实际处置结果</h4>
          <pre class="suggestion-pre">{{ current.solution }}</pre>
        </template>

        <h4 class="section-title">流转记录</h4>
        <el-timeline>
          <el-timeline-item
            v-for="log in logs"
            :key="log.id"
            :timestamp="fmtTime(log.createTime)"
            placement="top"
          >
            <div class="log-line">
              <el-tag size="small" effect="plain">
                {{ log.fromStatus ? statusLabel(log.fromStatus) : '创建' }}
                →
                {{ statusLabel(log.toStatus) }}
              </el-tag>
              <span class="log-op">{{ log.operator }}</span>
            </div>
            <div v-if="log.remark" class="log-remark">{{ log.remark }}</div>
          </el-timeline-item>
        </el-timeline>

        <div class="drawer-actions">
          <el-button
            v-for="action in nextActions(current.status)"
            :key="action.status"
            :type="action.type"
            @click="transit(current, action)"
          >
            {{ action.label }}
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { pageTickets, getTicketLogs, transitTicket } from '@/api/ticket'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const logs = ref([])
const drawerVisible = ref(false)
const current = ref(null)

const query = ref({
  pageNum: 1,
  pageSize: 10,
  status: '',
  priority: '',
  keyword: ''
})

const statuses = [
  { code: 'PENDING', label: '待处理' },
  { code: 'PROCESSING', label: '处理中' },
  { code: 'RESOLVED', label: '已解决' },
  { code: 'CLOSED', label: '已关闭' }
]

/** 依据当前状态给出可执行的流转动作，与后端状态机保持一致 */
function nextActions(status) {
  const map = {
    PENDING: [
      { status: 'PROCESSING', label: '接单', type: 'primary' },
      { status: 'CLOSED', label: '取消', type: 'info' }
    ],
    PROCESSING: [
      { status: 'RESOLVED', label: '解决', type: 'success' },
      { status: 'PENDING', label: '挂起', type: 'warning' }
    ],
    RESOLVED: [
      { status: 'CLOSED', label: '关闭', type: 'info' },
      { status: 'PROCESSING', label: '重开', type: 'warning' }
    ],
    CLOSED: []
  }
  return map[status] || []
}

async function load() {
  loading.value = true
  try {
    const data = await pageTickets({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      status: query.value.status || undefined,
      priority: query.value.priority || undefined,
      keyword: query.value.keyword || undefined
    })
    rows.value = data.records || []
    total.value = data.total || 0

    // 支持从分析页跳转后自动展开指定工单
    const highlight = route.query.highlight
    if (highlight) {
      const target = rows.value.find((r) => String(r.id) === String(highlight))
      if (target) openDetail(target)
    }
  } finally {
    loading.value = false
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: 10, status: '', priority: '', keyword: '' }
  load()
}

async function openDetail(row) {
  current.value = row
  drawerVisible.value = true
  logs.value = (await getTicketLogs(row.id)) || []
}

async function transit(row, action) {
  let remark
  if (action.status === 'RESOLVED') {
    const { value } = await ElMessageBox.prompt('请填写处置结果', '标记为已解决', {
      inputType: 'textarea',
      inputPlaceholder: '例如：已优化慢查询并调大连接池，指标恢复正常',
      inputValidator: (v) => (v && v.trim() ? true : '请填写处置结果')
    })
    remark = value
  } else {
    await ElMessageBox.confirm(
      `确定将工单 ${row.ticketNo} 变更为「${action.label}」吗？`,
      '提示',
      { type: 'warning' }
    )
  }

  await transitTicket(row.id, action.status, remark)
  ElMessage.success(`已变更为「${statusLabel(action.status)}」`)
  drawerVisible.value = false
  load()
}

function goEvent(eventId) {
  if (eventId) router.push(`/alerts/${eventId}/analyze`)
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
    PENDING: 'danger',
    PROCESSING: 'primary',
    RESOLVED: 'success',
    CLOSED: 'info'
  }[s] || 'info'
}

onMounted(load)
</script>

<style scoped>
.filter-bar {
  margin-bottom: 6px;
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

.suggestion-pre {
  background: #f7f9fd;
  border-radius: 6px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #33405c;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
}

.log-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.log-op {
  font-size: 12px;
  color: #8892a8;
}

.log-remark {
  font-size: 12px;
  color: #6b7a99;
  margin-top: 4px;
}

.drawer-actions {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #eef1f7;
  display: flex;
  gap: 10px;
}
</style>
