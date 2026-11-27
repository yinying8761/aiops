<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <h1>AI 智能运维告警分析与处置平台</h1>
        <p class="subtitle">AIOps · 多 Agent 协作 · 告警聚合 · 根因分析</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="tips">
        <div class="tips-title">测试账号</div>
        <div class="tip-row"><span>admin</span> / admin123 <em>（系统管理员）</em></div>
        <div class="tip-row"><span>ops_duty</span> / 123456 <em>（值班负责人）</em></div>
        <div class="tip-row"><span>ops_backend</span> / 123456 <em>（运维工程师）</em></div>
      </div>
    </div>

    <div class="footer">《软件工程综合实践R》SJ003303 · 第 11 组「不想动项目组」</div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/dashboard')
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0d1526 0%, #1b2b52 55%, #2b5fdf 100%);
}

.login-card {
  width: 420px;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 14px;
  padding: 36px 34px 26px;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.35);
}

.brand {
  text-align: center;
  margin-bottom: 26px;
}

.brand-mark {
  display: inline-block;
  background: #2b5fdf;
  color: #fff;
  font-weight: 700;
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 18px;
  margin-bottom: 12px;
}

.brand h1 {
  font-size: 19px;
  margin: 0 0 6px;
  color: #1f2740;
}

.subtitle {
  margin: 0;
  font-size: 12px;
  color: #8892a8;
}

.login-btn {
  width: 100%;
  margin-top: 6px;
}

.tips {
  margin-top: 22px;
  padding-top: 16px;
  border-top: 1px dashed #e2e7f0;
  font-size: 12px;
  color: #6b7a99;
}

.tips-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #445072;
}

.tip-row {
  line-height: 1.9;
}

.tip-row span {
  font-family: Consolas, monospace;
  color: #2b5fdf;
  font-weight: 600;
}

.tip-row em {
  color: #98a2b8;
  font-style: normal;
}

.footer {
  margin-top: 22px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 12px;
}
</style>
