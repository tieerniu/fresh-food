<template>
  <div class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '78px' : '248px'" class="sidebar">
      <div class="logo-container">
        <div class="logo-badge">
          <el-icon class="logo-icon"><Management /></el-icon>
        </div>
        <div v-if="!isCollapse" class="logo-copy">
          <h2>溯源后台</h2>
          <p>Fresh Trace Console</p>
        </div>
      </div>

      <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :router="true"
          background-color="transparent"
          text-color="#d8deea"
          active-text-color="#ffffff"
          class="side-menu"
      >
        <el-menu-item index="/admin/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>控制台</template>
        </el-menu-item>

        <el-menu-item index="/admin/products">
          <el-icon><Box /></el-icon>
          <template #title>产品管理</template>
        </el-menu-item>

        <el-menu-item v-if="isAdmin" index="/admin/suppliers">
          <el-icon><OfficeBuilding /></el-icon>
          <template #title>合作基地</template>
        </el-menu-item>

        <el-menu-item index="/admin/records">
          <el-icon><List /></el-icon>
          <template #title>溯源记录管理</template>
        </el-menu-item>

        <el-menu-item v-if="isAdmin" index="/admin/qrcodes">
          <el-icon><Grid /></el-icon>
          <template #title>二维码管理</template>
        </el-menu-item>

        <el-menu-item v-if="isAdmin" index="/admin/inspections">
          <el-icon style="color: #e6a23c"><WarnTriangleFilled /></el-icon>
          <template #title>质检与召回</template>
        </el-menu-item>

        <el-menu-item v-if="isAdmin" index="/admin/warnings" class="warning-menu-item">
          <el-icon style="color: #f56c6c"><Warning /></el-icon>
          <template #title>防伪预警中心</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 顶栏 -->
      <el-header class="header">
        <div class="header-left">
          <div class="collapse-trigger" @click="toggleCollapse">
            <el-icon class="collapse-icon">
              <Expand v-if="isCollapse" />
              <Fold v-else />
            </el-icon>
          </div>
          <div class="header-copy">
            <span class="header-kicker">{{ currentSection }}</span>
            <h1 class="header-title">生鲜溯源管理系统</h1>
          </div>
        </div>

        <div class="header-right">
          <div class="layout-badge">
            <span class="status-dot"></span>
            {{ isAdmin ? '管理员视图' : '基地视图' }}
          </div>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-icon class="user-icon"><User /></el-icon>
              <span class="username">{{ username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { logout } from '@/api/user'
import {
  Management,
  Odometer,
  Box,
  List,
  Grid,
  User,
  ArrowDown,
  SwitchButton,
  Expand,
  Fold,
  OfficeBuilding,
  Warning,
  WarnTriangleFilled
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapse = ref(false)
const username = ref('')
const userRole = ref('')

// 是否为管理员角色
const isAdmin = computed(() => userRole.value === 'admin')

// 当前激活的菜单
const activeMenu = computed(() => {
  return route.path
})

const currentSection = computed(() => route.meta?.title || '工作台')

// 页面加载时获取用户信息
onMounted(() => {
  const userInfo = localStorage.getItem('userInfo')
  if (userInfo) {
    const user = JSON.parse(userInfo)
    username.value = user.username
    userRole.value = user.role || ''
  } else {
    // 如果没有登录信息，跳转到登录页
    ElMessage.warning('请先登录')
    router.push('/login')
  }
})

// 切换侧边栏折叠状态
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 下拉菜单命令处理
const handleCommand = (command) => {
  if (command === 'profile') {
    router.push('/admin/profile')
  } else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      logout().finally(() => {
        ElMessage.success('已退出登录')
        router.push('/login')
      })
    }).catch(() => {
      // 取消退出
    })
  }
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background:
      radial-gradient(circle at top left, rgba(37, 99, 235, 0.08), transparent 24%),
      linear-gradient(180deg, #f5f8fb 0%, #edf3f7 100%);
}

/* 侧边栏 */
.sidebar {
  background:
      linear-gradient(180deg, #152233 0%, #1b3047 42%, #233c55 100%);
  transition: width 0.28s ease;
  overflow-x: hidden;
  box-shadow: 18px 0 40px rgba(15, 23, 42, 0.12);
}

.logo-container {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 88px;
  padding: 0 22px;
  background: rgba(255, 255, 255, 0.04);
  color: white;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-badge {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0.06));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.14);
}

.logo-icon {
  font-size: 24px;
}

.logo-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.logo-container h2 {
  font-size: 20px;
  margin: 0;
  letter-spacing: 0.03em;
}

.logo-copy p {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: rgba(226, 232, 240, 0.7);
  text-transform: uppercase;
}

.side-menu {
  border-right: none;
  padding: 18px 12px 24px;
}

.side-menu :deep(.el-menu-item) {
  height: 50px;
  margin-bottom: 8px;
  border-radius: 16px;
  font-weight: 600;
  color: #d7deea;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08) !important;
  color: #ffffff !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #2f6df6, #4694ff) !important;
  color: #ffffff !important;
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.28);
}

.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 18px;
}

/* 主内容区 */
.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: transparent;
}

/* 顶栏 */
.header {
  height: 88px;
  background: rgba(255, 255, 255, 0.84);
  backdrop-filter: blur(18px);
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 28px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-trigger {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 14px;
  background: rgba(37, 99, 235, 0.08);
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.collapse-trigger:hover {
  transform: translateY(-1px);
  background: rgba(37, 99, 235, 0.14);
}

.collapse-icon {
  font-size: 20px;
  color: #2563eb;
}

.header-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-kicker {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7a8699;
}

.header-title {
  font-size: 24px;
  color: #162033;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.layout-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2852b8;
  font-size: 13px;
  font-weight: 600;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.14);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.82);
  transition: background-color 0.3s, transform 0.2s ease;
}

.user-info:hover {
  background-color: #f8fbff;
  transform: translateY(-1px);
}

.user-icon {
  font-size: 20px;
  color: #2563eb;
}

.username {
  font-size: 14px;
  color: #1f2937;
  font-weight: 600;
}

/* 内容区 */
.main-content {
  padding: 28px;
  overflow-y: auto;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .sidebar {
    box-shadow: none;
  }

  .header {
    padding: 0 16px;
  }

  .header-title {
    font-size: 18px;
  }

  .username {
    display: none;
  }

  .main-content {
    padding: 16px;
  }

  .layout-badge {
    display: none;
  }
}
</style>
