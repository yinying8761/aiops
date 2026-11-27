<template>
  <div>
    <div class="card-panel">
      <div class="panel-head">
        <div>
          <span class="panel-title">处置经验知识库</span>
          <span class="sub-hint">沉淀「症状 — 根因 — 解法」，供处置建议 Agent 复用</span>
        </div>
        <el-button type="primary" :icon="Plus" @click="openEdit()">新增模板</el-button>
      </div>

      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="标题 / 症状 / 标签"
            clearable
            style="width: 220px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="query.category" placeholder="全部" clearable style="width: 150px">
            <el-option
              v-for="c in categories"
              :key="c.category"
              :label="`${c.category} (${c.count})`"
              :value="c.category"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="title" label="模板标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.category || 'OTHER' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="symptom" label="症状" min-width="220" show-overflow-tooltip />
        <el-table-column prop="tags" label="标签" width="180" show-overflow-tooltip />
        <el-table-column label="复用次数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.useCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="showDetail(row)">查看</el-button>
            <el-button link type="warning" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
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

    <!-- 查看/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑模板' : '模板详情'"
      width="760px"
    >
      <el-form v-if="!editing" label-width="90px">
        <el-form-item label="标题">{{ form.title }}</el-form-item>
        <el-form-item label="分类">
          <el-tag size="small">{{ form.category }}</el-tag>
        </el-form-item>
        <el-form-item label="标签">
          <el-tag
            v-for="t in (form.tags || '').split(',').filter(Boolean)"
            :key="t"
            size="small"
            style="margin-right: 6px"
          >
            {{ t }}
          </el-tag>
        </el-form-item>
        <el-form-item label="症状">{{ form.symptom }}</el-form-item>
        <el-form-item label="根因">{{ form.rootCause }}</el-form-item>
        <el-form-item label="解法">
          <pre class="solution-pre">{{ form.solution }}</pre>
        </el-form-item>
      </el-form>

      <el-form v-else ref="editFormRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="如：数据库连接池耗尽" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="form.tags" placeholder="逗号分隔，如：database,连接池,慢SQL" />
        </el-form-item>
        <el-form-item label="症状" prop="symptom">
          <el-input
            v-model="form.symptom"
            type="textarea"
            :rows="3"
            placeholder="描述故障表现，如：应用抛出无法获取数据库连接异常"
          />
        </el-form-item>
        <el-form-item label="根因" prop="rootCause">
          <el-input v-model="form.rootCause" type="textarea" :rows="2" placeholder="根因分析结论" />
        </el-form-item>
        <el-form-item label="解法" prop="solution">
          <el-input
            v-model="form.solution"
            type="textarea"
            :rows="6"
            placeholder="分步处置方案，每行一步"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ editing ? '取消' : '关闭' }}</el-button>
        <el-button v-if="editing" type="primary" :loading="saving" @click="save">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import {
  pageTemplates,
  getTemplate,
  saveTemplate,
  deleteTemplate,
  getCategories
} from '@/api/knowledge'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])
const dialogVisible = ref(false)
const editing = ref(false)
const editFormRef = ref()

const query = ref({ pageNum: 1, pageSize: 10, keyword: '', category: '' })

const categoryOptions = [
  'CPU',
  'MEMORY',
  'NETWORK',
  'DISK',
  'APPLICATION',
  'DATABASE',
  'MIDDLEWARE',
  'OTHER'
]

const form = ref(blankForm())

const rules = {
  title: [{ required: true, message: '请填写标题', trigger: 'blur' }],
  symptom: [{ required: true, message: '请填写症状', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

function blankForm() {
  return {
    id: null,
    title: '',
    category: 'DATABASE',
    tags: '',
    symptom: '',
    rootCause: '',
    solution: ''
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageTemplates({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      keyword: query.value.keyword || undefined,
      category: query.value.category || undefined
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadCategories() {
  try {
    categories.value = await getCategories()
  } catch (e) {
    categories.value = []
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: 10, keyword: '', category: '' }
  load()
}

async function showDetail(row) {
  editing.value = false
  form.value = await getTemplate(row.id)
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  form.value = row ? { ...row } : blankForm()
  dialogVisible.value = true
}

async function save() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await saveTemplate(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
    loadCategories()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除模板「${row.title}」？`, '提示', { type: 'warning' })
  await deleteTemplate(row.id)
  ElMessage.success('已删除')
  load()
  loadCategories()
}

onMounted(() => {
  load()
  loadCategories()
})
</script>

<style scoped>
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2740;
}

.sub-hint {
  display: block;
  font-size: 12px;
  color: #98a2b8;
  margin-top: 4px;
}

.filter-bar {
  margin-bottom: 6px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.solution-pre {
  background: #f7f9fd;
  padding: 12px;
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.8;
  white-space: pre-wrap;
  margin: 0;
  font-family: inherit;
  color: #33405c;
}
</style>
