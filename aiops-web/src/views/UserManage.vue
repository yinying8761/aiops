<template>
  <div>
    <div class="card-panel">
      <div class="panel-head">
        <span class="panel-title">用户管理</span>
        <el-button type="primary" :icon="Plus" @click="openEdit()">新增用户</el-button>
      </div>

      <el-form :inline="true" class="filter-bar">
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="用户名 / 昵称"
            clearable
            style="width: 200px"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column label="角色" width="180">
          <template #default="{ row }">
            <el-tag
              v-for="r in row.roles"
              :key="r"
              size="small"
              style="margin-right: 4px"
              effect="plain"
            >
              {{ r }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :disabled="row.id === 1"
              @change="(v) => toggleStatus(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.id === 1"
              @click="remove(row)"
            >
              删除
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户' : '新增用户'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="字母数字下划线" />
        </el-form-item>
        <el-form-item label="密码" :prop="form.id ? '' : 'password'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="form.id ? '留空表示不修改' : '至少 6 位'"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色" prop="roleCodes">
          <el-select v-model="form.roleCodes" multiple style="width: 100%">
            <el-option v-for="r in roleOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh } from '@element-plus/icons-vue'
import { pageUsers, saveUser, deleteUser, listRoles } from '@/api/auth'
import http from '@/api/http'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const roleOptions = ref([])
const dialogVisible = ref(false)
const formRef = ref()

const query = ref({ pageNum: 1, pageSize: 10, keyword: '', status: undefined })

const form = ref(blankForm())

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{3,50}$/, message: '3~50 位字母数字下划线', trigger: 'blur' }
  ],
  password: [
    { min: 6, max: 64, message: '密码长度 6~64 位', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

function blankForm() {
  return {
    id: null,
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    status: 1,
    roleCodes: []
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pageUsers({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      keyword: query.value.keyword || undefined,
      status: query.value.status
    })
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    roleOptions.value = await listRoles()
  } catch (e) {
    roleOptions.value = ['ADMIN', 'OPS', 'MANAGER']
  }
}

function search() {
  query.value.pageNum = 1
  load()
}

function reset() {
  query.value = { pageNum: 1, pageSize: 10, keyword: '', status: undefined }
  load()
}

function openEdit(row) {
  form.value = row ? { ...row, password: '' } : blankForm()
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await saveUser(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('已删除')
  load()
}

async function toggleStatus(row, enabled) {
  const status = enabled ? 1 : 0
  try {
    await http.put(`/auth/users/${row.id}/status`, null, { params: { status } })
    row.status = status
    ElMessage.success(status === 1 ? '已启用' : '已禁用')
  } catch (e) {
    load()
  }
}

function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2740;
}

.filter-bar {
  margin-bottom: 6px;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
