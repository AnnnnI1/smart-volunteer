<template>
  <el-container class="layout-container">
    <!-- 顶部导航 -->
    <el-header class="layout-header">
      <div class="header-left">
        <el-icon class="logo-icon"><Star /></el-icon>
        <span class="logo-text">智能志愿者管理系统</span>
      </div>
      <div class="header-right">
        <span class="welcome-text">欢迎，{{ userInfo?.nickname || userInfo?.username }}</span>
        <el-tag :type="isAdmin ? 'danger' : isOrganizer ? 'warning' : 'success'" size="small" style="margin: 0 12px">
          {{ isAdmin ? '管理员' : isOrganizer ? '组织者' : '志愿者' }}
        </el-tag>

        <!-- 信封按钮（仅志愿者和组织者可见） -->
        <el-badge v-if="!isAdmin" :value="unreadCount" :hidden="unreadCount === 0" :max="99" style="margin-right:12px">
          <el-button circle size="small" @click="openInvitationDrawer">
            <el-icon><Message /></el-icon>
          </el-button>
        </el-badge>

        <el-button type="danger" plain size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container>
      <!-- 左侧菜单 -->
      <el-aside width="200px" class="layout-aside">
        <el-menu
          :default-active="$route.path"
          router
          background-color="#001529"
          text-color="#ffffffa6"
          active-text-color="#ffffff"
          class="side-menu"
        >
          <!-- 志愿者菜单 -->
          <template v-if="!isAdmin && !isOrganizer">
            <el-menu-item index="/homepage/dashboard">
              <el-icon><House /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/homepage/recommend">
              <el-icon><MagicStick /></el-icon>
              <span>为我推荐</span>
            </el-menu-item>
            <el-menu-item index="/homepage/activities">
              <el-icon><List /></el-icon>
              <span>活动大厅</span>
            </el-menu-item>
            <el-menu-item index="/homepage/profile">
              <el-icon><User /></el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </template>

          <!-- 组织者菜单 -->
          <template v-else-if="isOrganizer">
            <el-menu-item index="/homepage/dashboard">
              <el-icon><House /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/homepage/recommend">
              <el-icon><MagicStick /></el-icon>
              <span>为我推荐</span>
            </el-menu-item>
            <el-menu-item index="/homepage/activities">
              <el-icon><List /></el-icon>
              <span>活动大厅</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/activities">
              <el-icon><Setting /></el-icon>
              <span>我的活动管理</span>
            </el-menu-item>
            <el-menu-item index="/homepage/profile">
              <el-icon><User /></el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </template>

          <!-- 管理员菜单 -->
          <template v-else>
            <el-menu-item index="/homepage/dashboard">
              <el-icon><House /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/homepage/activities">
              <el-icon><List /></el-icon>
              <span>活动大厅</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/activities">
              <el-icon><Setting /></el-icon>
              <span>活动管理</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/users">
              <el-icon><UserFilled /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/ai">
              <el-icon><DataAnalysis /></el-icon>
              <span>AI运营中心</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/audit">
              <el-icon><Warning /></el-icon>
              <span>活动风控日志</span>
            </el-menu-item>
            <el-menu-item index="/homepage/admin/nl2sql">
              <el-icon><ChatLineRound /></el-icon>
              <span>智能数据查询</span>
            </el-menu-item>
            <el-menu-item index="/homepage/profile">
              <el-icon><User /></el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 邀请弹窗（放在 el-container 外，避免影响 router-view 渲染） -->
  <el-dialog
      v-model="invitationVisible"
      title="我的活动邀请"
      width="600px"
      destroy-on-close
      @open="onDrawerOpen"
    >
      <div v-if="invitationLoading" v-loading="true" style="height:120px" />
      <div v-else-if="!invitationList.length" style="text-align:center;padding:40px 0;color:#c0c4cc">
        <el-icon style="font-size:48px"><Message /></el-icon>
        <div style="margin-top:12px">暂无邀请</div>
      </div>
      <div v-else>
        <div
          v-for="item in invitationList"
          :key="item.invitationId"
          class="inv-card"
          :class="{ unread: item.isRead === 0 }"
        >
          <div class="inv-card-body">
            <div class="inv-title">{{ item.title }}</div>
            <div class="inv-meta">
              <el-tag :type="statusTagType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
              <span style="margin-left:8px;font-size:12px;color:#909399">
                {{ item.startTime?.substring(0, 10) }} ~ {{ item.endTime?.substring(0, 10) }}
              </span>
              <span style="margin-left:8px;font-size:12px;color:#909399">
                剩余名额：<b :style="{ color: item.remainQuota > 0 ? '#67c23a' : '#f56c6c' }">{{ item.remainQuota }}</b>
              </span>
            </div>
            <div v-if="item.requiredSkills" style="margin-top:6px">
              <el-tag
                v-for="s in item.requiredSkills.split(',')"
                :key="s"
                size="small"
                effect="plain"
                style="margin:2px"
              >{{ s }}</el-tag>
            </div>
            <div style="font-size:12px;color:#c0c4cc;margin-top:4px">
              邀请时间：{{ item.inviteTime?.substring(0, 16)?.replace('T', ' ') }}
            </div>
          </div>
          <div style="display:flex;flex-direction:column;gap:6px">
            <el-button
              type="primary"
              size="small"
              :disabled="item.status === 3 || item.remainQuota <= 0"
              @click="goRegister(item.activityId)"
            >去报名</el-button>
            <el-button
              type="danger"
              plain
              size="small"
              @click="handleDelete(item.invitationId)"
            >删除</el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="invitationVisible = false">关闭</el-button>
      </template>
    </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import {
  Star, List, User, Setting, ChatLineRound, House, MagicStick,
  UserFilled, DataAnalysis, Message, Warning
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyInvitations, markInvitationsRead, getUnreadInvitationCount, deleteInvitation } from '@/api/activity'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const isAdmin = computed(() => authStore.userInfo?.role === 0)
const isOrganizer = computed(() => authStore.userInfo?.role === 2)

const unreadCount = ref(0)
const invitationVisible = ref(false)
const invitationLoading = ref(false)
const invitationList = ref([])

const statusLabel = (s) => ['未开始', '报名中', '进行中', '已结束'][s] ?? '未知'
const statusTagType = (s) => ['info', 'primary', 'success', ''][s] ?? 'info'

const loadUnreadCount = async () => {
  if (isAdmin.value) return
  try {
    const res = await getUnreadInvitationCount()
    unreadCount.value = res.data ?? 0
  } catch (e) {}
}

const openInvitationDrawer = () => { invitationVisible.value = true }

const onDrawerOpen = async () => {
  invitationLoading.value = true
  try {
    const res = await getMyInvitations()
    invitationList.value = res.data?.list || []
    unreadCount.value = 0
    await markInvitationsRead()
  } catch (e) {} finally {
    invitationLoading.value = false
  }
}

const goRegister = (activityId) => {
  invitationVisible.value = false
  router.push(`/homepage/activity/${activityId}`)
}

const handleDelete = async (invitationId) => {
  try {
    await ElMessageBox.confirm('确定删除这条邀请？', '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await deleteInvitation(invitationId)
    invitationList.value = invitationList.value.filter(i => i.invitationId !== invitationId)
    ElMessage.success('已删除')
  } catch (e) {}
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  }).then(() => {
    authStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

onMounted(loadUnreadCount)
</script>

<style scoped>
.layout-container {
  height: 100vh;
  overflow: hidden;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #001529, #003a70);
  padding: 0 20px;
  height: 56px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.3);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  font-size: 24px;
  color: #409EFF;
}

.logo-text {
  font-size: 18px;
  font-weight: bold;
  color: #ffffff;
  letter-spacing: 1px;
}

.header-right {
  display: flex;
  align-items: center;
}

.welcome-text {
  color: #ffffffa6;
  font-size: 14px;
}

.layout-aside {
  background-color: #001529;
  height: calc(100vh - 56px);
  overflow-y: auto;
}

.side-menu {
  border-right: none;
  height: 100%;
}

.layout-main {
  background-color: #f0f2f5;
  padding: 20px;
  height: calc(100vh - 56px);
  overflow-y: auto;
}

/* 邀请卡片 */
.inv-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
  transition: background 0.2s;
}
.inv-card.unread {
  border-left: 3px solid #409EFF;
  background: #f0f7ff;
}
.inv-card-body { flex: 1; margin-right: 12px; }
.inv-title { font-weight: bold; font-size: 14px; color: #303133; }
.inv-meta { margin-top: 4px; display: flex; align-items: center; flex-wrap: wrap; }
</style>
