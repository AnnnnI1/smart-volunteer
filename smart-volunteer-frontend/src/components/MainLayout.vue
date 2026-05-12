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

        <!-- 消息入口（邀请 + 举报通知合并） -->
        <el-badge :value="totalUnread" :hidden="totalUnread === 0" :max="99" style="margin-right:12px">
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
            <el-menu-item index="/homepage/admin/publish-assistant">
              <el-icon><ChatLineRound /></el-icon>
              <span>智能发布助手</span>
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
            <el-menu-item index="/homepage/admin/feedback-center">
              <el-icon><Bell /></el-icon>
              <span>负反馈中心</span>
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

  <!-- 我的消息弹窗（邀请 + 举报通知合并） -->
  <el-dialog
      v-model="invitationVisible"
      title="我的消息"
      width="600px"
      destroy-on-close
      @open="onDrawerOpen"
    >
      <template #header>
        <div style="display:flex;align-items:center;gap:12px">
          <el-radio-group v-model="msgTab" size="small">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="invitation">活动邀请</el-radio-button>
            <el-radio-button value="notification">举报通知</el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <div v-if="invitationLoading" v-loading="true" style="height:120px" />
      <div v-else-if="!displayMessageList.length" style="text-align:center;padding:40px 0;color:#c0c4cc">
        <el-icon style="font-size:48px"><Message /></el-icon>
        <div style="margin-top:12px">暂无消息</div>
      </div>
      <div v-else>
        <!-- 活动邀请 -->
        <template v-for="item in displayMessageList" :key="'inv-' + item.invitationId">
          <div
            v-if="item._type === 'invitation'"
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
                @click="handleDeleteInvitation(item.invitationId)"
              >删除</el-button>
            </div>
          </div>
        </template>

        <!-- 举报通知 -->
        <template v-for="item in displayMessageList" :key="'notif-' + item.id">
          <div
            v-if="item._type === 'notification'"
            class="notif-card"
            :class="{ unread: item.isRead === 0 }"
            @click="handleReadNotification(item)"
          >
            <div class="notif-icon">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="notif-body">
              <div class="notif-title">{{ item.title }}</div>
              <div class="notif-content">{{ item.content }}</div>
              <div class="notif-time">{{ fmt(item.createdAt) }}</div>
            </div>
            <div v-if="item.isRead === 0" class="notif-dot" />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="handleMarkAllRead">全部标为已读</el-button>
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
  UserFilled, DataAnalysis, Message, Warning, Bell
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyInvitations, markInvitationsRead, getUnreadInvitationCount, deleteInvitation } from '@/api/activity'
import { getUnreadNotificationCount, getNotificationList, markNotificationRead, markAllNotificationsRead } from '@/api/notification'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const isAdmin = computed(() => authStore.userInfo?.role === 0)
const isOrganizer = computed(() => authStore.userInfo?.role === 2)

// 合并未读数（邀请 + 举报通知）
const totalUnread = ref(0)
const invitationUnread = ref(0)
const notificationUnread = ref(0)

const invitationVisible = ref(false)
const invitationLoading = ref(false)
const invitationList = ref([])
const notificationList = ref([])
const msgTab = ref('all')

const statusLabel = (s) => ['未开始', '报名中', '进行中', '已结束'][s] ?? '未知'
const statusTagType = (s) => ['info', 'primary', 'success', ''][s] ?? 'info'

const displayMessageList = computed(() => {
  const invs = (invitationList.value || []).map(i => ({ ...i, _type: 'invitation' }))
  const notifs = (notificationList.value || []).map(n => ({ ...n, _type: 'notification' }))
  const all = [...invs, ...notifs].sort((a, b) => {
    const ta = a.createdAt || a.inviteTime || ''
    const tb = b.createdAt || b.inviteTime || ''
    return tb.localeCompare(ta)
  })
  if (msgTab.value === 'invitation') return invs
  if (msgTab.value === 'notification') return notifs
  return all
})

const loadUnreadCount = async () => {
  if (isAdmin.value) return
  try {
    const [invRes, notifRes] = await Promise.all([
      getUnreadInvitationCount().catch(() => ({ data: 0 })),
      getUnreadNotificationCount().catch(() => ({ data: 0 }))
    ])
    invitationUnread.value = invRes.data ?? 0
    notificationUnread.value = notifRes.data?.unreadCount ?? 0
    totalUnread.value = invitationUnread.value + notificationUnread.value
  } catch (e) {}
}

const openInvitationDrawer = () => { invitationVisible.value = true }

const onDrawerOpen = async () => {
  invitationLoading.value = true
  try {
    const [invRes, notifRes] = await Promise.all([
      getMyInvitations().catch(() => ({ data: { list: [] } })),
      getNotificationList({ page: 1, size: 50 }).catch(() => ({ data: { rows: [] } }))
    ])
    invitationList.value = invRes.data?.list || []
    notificationList.value = notifRes.data?.rows || []
    // 标记已读
    await markInvitationsRead().catch(() => {})
    await markAllNotificationsRead().catch(() => {})
    totalUnread.value = 0
    invitationUnread.value = 0
    notificationUnread.value = 0
  } catch (e) {} finally {
    invitationLoading.value = false
  }
}

const goRegister = (activityId) => {
  invitationVisible.value = false
  router.push(`/homepage/activity/${activityId}`)
}

const handleDeleteInvitation = async (invitationId) => {
  try {
    await ElMessageBox.confirm('确定删除这条邀请？', '提示', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    await deleteInvitation(invitationId)
    invitationList.value = invitationList.value.filter(i => i.invitationId !== invitationId)
    ElMessage.success('已删除')
  } catch (e) {}
}

const handleReadNotification = async (item) => {
  if (item.isRead === 0) {
    await markNotificationRead(item.id).catch(() => {})
    item.isRead = 1
  }
}

const handleMarkAllRead = async () => {
  await markAllNotificationsRead().catch(() => {})
  notificationList.value.forEach(n => { n.isRead = 1 })
  ElMessage.success('已全部标为已读')
}

const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').substring(0, 16) : ''

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

/* 通知卡片 */
.notif-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}
.notif-card:hover { background: #f5f7fa; }
.notif-card.unread { background: #f0f7ff; }
.notif-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff1f0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #f56c6c;
  flex-shrink: 0;
  font-size: 16px;
}
.notif-body { flex: 1; }
.notif-title { font-weight: bold; font-size: 13px; color: #303133; margin-bottom: 4px; }
.notif-content { font-size: 12px; color: #606266; line-height: 1.5; margin-bottom: 4px; }
.notif-time { font-size: 11px; color: #c0c4cc; }
.notif-dot {
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 4px;
}
</style>
