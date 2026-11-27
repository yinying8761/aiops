<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span class="logo-mark">AI</span>
        <span v-show="!collapsed" class="logo-text">AIOps 平台</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        background-color="#131a2b"
        text-color="#b8c4dd"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item
          v-for="item in menus"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <component :is="collapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <span class="page-name">{{ currentTitle }}</span>
        </div>

        <div class="header-right">
          <el-tag
            v-if="provider"
            size="small"
            :type="provider === 'mock' ? 'warning' : 'success'"
            class="llm-tag"
          >
            LLM: {{ provider === 'mock' ? '模拟模型' : 'DeepSeek' }}
          </el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.nickname }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  角色：{{ userStore.roles.join(' / ') || '无' }}
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { getProvider } from '@/api/agent'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const provider = ref('')

const menus = computed(() => {
  const main = router.options.routes.find((r) => r.path === '/')
  return (main?.children || [])
    .filter((c) => !c.meta?.hidden)
    .filter((c) => !c.meta?.adminOnly || userStore.isAdmin)
    .map((c) => ({
      path: '/' + c.path,
      title: c.meta.title,
      icon: c.meta.icon
    }))
})

const activeMenu = computed(() => {
  // 分析页高亮到告警中心
  if (route.path.includes('/analyze')) return '/alerts'
  return route.path
})

const currentTitle = computed(() => route.meta?.title || 'AIOps 平台')

onMounted(async () => {
  await userStore.refresh()
  try {
    const p = await getProvider()
    provider.value = p.provider
  } catch (e) {
    provider.value = ''
  }
})

async function handleCommand(cmd) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  background: #131a2b;
  transition: width 0.2s;
  overflow-x: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  border-bottom: 1px solid #222c44;
}

.logo-mark {
  background: #2b5fdf;
  color: #fff;
  font-weight: 700;
  font-size: 13px;
  padding: 3px 7px;
  border-radius: 4px;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.aside :deep(.el-menu) {
  border-right: none;
}

.header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e6eaf2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  /* 防止右侧内容被裁切 */
  overflow: visible;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  cursor: pointer;
  font-size: 18px;
  color: #58637d;
}

.page-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2740;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.llm-tag {
  flex-shrink: 0;
  white-space: nowrap;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #33405c;
}

.main {
  background: #f4f6fa;
  padding: 18px;
  overflow-y: auto;
}
</style>
