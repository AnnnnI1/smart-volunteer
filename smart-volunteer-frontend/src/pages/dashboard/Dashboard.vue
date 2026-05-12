<template>
  <div class="dashboard">

    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="welcome-left">
        <div class="welcome-greeting">{{ greeting }}，{{ userInfo?.nickname || userInfo?.username }} 👋</div>
        <div class="welcome-sub">
          <el-tag :type="isAdmin ? 'danger' : isOrganizer ? 'warning' : 'success'" effect="dark" size="small" style="margin-right:8px">
            {{ isAdmin ? '管理员' : isOrganizer ? '组织者' : '志愿者' }}
          </el-tag>
          {{ today }}
        </div>
      </div>
      <div class="welcome-right">
        <el-button type="primary" @click="$router.push('/homepage/activities')">
          <el-icon><List /></el-icon>&nbsp;活动大厅
        </el-button>
        <el-button v-if="isAdmin || isOrganizer" @click="$router.push('/homepage/admin/activities')" style="margin-left:10px">
          <el-icon><Setting /></el-icon>&nbsp;活动管理
        </el-button>
      </div>
    </div>

    <!-- ── 管理员视图 ── -->
    <template v-if="isAdmin">
      <!-- 统计卡片 -->
      <el-row :gutter="16" style="margin-bottom:20px">
        <el-col :span="6" v-for="card in statCards" :key="card.label">
          <div
            class="stat-card"
            :style="{borderColor: card.color, cursor: card.link ? 'pointer' : 'default'}"
            @click="card.link && $router.push(card.link)"
          >
            <el-icon :style="{color: card.color, fontSize:'28px'}">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-num" :style="{color: card.color}">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
            <div v-if="card.link" style="font-size:11px;color:#409EFF;margin-top:2px">点击查看 →</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <!-- 活动状态分布饼图 -->
        <el-col :span="10">
          <el-card class="chart-card" shadow="never">
            <template #header><span class="card-title"><el-icon><PieChart /></el-icon> 活动状态分布</span></template>
            <div ref="pieChartRef" style="height:240px" />
          </el-card>
        </el-col>

        <!-- 功能快捷入口 -->
        <el-col :span="14">
          <el-card class="chart-card" shadow="never">
            <template #header><span class="card-title"><el-icon><Menu /></el-icon> 快捷入口</span></template>
            <el-row :gutter="12">
              <el-col :span="12" v-for="item in adminShortcuts" :key="item.title">
                <div class="shortcut-card" @click="$router.push(item.path)">
                  <el-icon :style="{color: item.color, fontSize:'32px'}">
                    <component :is="item.icon" />
                  </el-icon>
                  <div class="shortcut-title">{{ item.title }}</div>
                  <div class="shortcut-desc">{{ item.desc }}</div>
                </div>
              </el-col>
            </el-row>
          </el-card>
        </el-col>
      </el-row>

      <!-- 最近活动列表 -->
      <el-card class="chart-card" shadow="never" style="margin-top:16px">
        <template #header><span class="card-title"><el-icon><Clock /></el-icon> 最近活动</span></template>
        <el-table :data="recentActivities" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="活动名称" />
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="名额进度" width="160">
            <template #default="{row}">
              <el-progress
                :percentage="Math.min(100, Math.round(row.joinedQuota/row.totalQuota*100))"
                :stroke-width="8" :show-text="false"
                :color="row.remainQuota===0?'#f56c6c':'#409EFF'"
              />
              <span style="font-size:11px;color:#909399">{{ row.joinedQuota }}/{{ row.totalQuota }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="$router.push(`/homepage/activity/${row.id}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <!-- ── 组织者视图 ── -->
    <template v-else-if="isOrganizer">
      <el-row :gutter="16" style="margin-bottom:20px">
        <el-col :span="6" v-for="card in orgStatCards" :key="card.label">
          <div class="stat-card" :style="{borderColor: card.color}">
            <el-icon :style="{color: card.color, fontSize:'28px'}">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-num" :style="{color: card.color}">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </el-col>
      </el-row>
      <el-card class="chart-card" shadow="never">
        <template #header><span class="card-title"><el-icon><Clock /></el-icon> 我发起的活动</span></template>
        <el-table :data="myOrgActivities" size="small">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="活动名称" />
          <el-table-column label="状态" width="90">
            <template #default="{row}">
              <el-tag :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="名额" width="120">
            <template #default="{row}">
              <el-progress :percentage="Math.min(100,Math.round(row.joinedQuota/row.totalQuota*100))" :stroke-width="8" :show-text="false" />
              <span style="font-size:11px;color:#909399">{{ row.joinedQuota }}/{{ row.totalQuota }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{row}">
              <el-button link type="primary" size="small" @click="$router.push('/homepage/admin/activities')">管理</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin-top:12px;text-align:right">
          <el-button type="primary" size="small" @click="$router.push('/homepage/admin/activities')">
            <el-icon><Setting /></el-icon>&nbsp;进入活动管理
          </el-button>
        </div>
      </el-card>
    </template>

    <!-- ── 志愿者视图 ── -->
    <template v-else>
      <el-row :gutter="16">
        <!-- 技能标签云 -->
        <el-col :span="10">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span class="card-title"><el-icon><Medal /></el-icon> 我的技能档案</span>
                <el-button link type="primary" size="small" @click="$router.push('/homepage/profile')">完善档案 →</el-button>
              </div>
            </template>
            <div v-if="myProfile.skills?.length">
              <el-tag
                v-for="s in myProfile.skills"
                :key="s"
                type="primary" effect="light"
                style="margin:5px;font-size:14px;padding:6px 14px"
              >{{ s }}</el-tag>
            </div>
            <el-empty v-else description="还未添加技能，去个人中心完善档案" :image-size="80">
              <el-button type="primary" size="small" @click="$router.push('/homepage/profile')">立即完善</el-button>
            </el-empty>
            <el-divider />
            <div style="color:#606266;font-size:13px">
              <el-icon><Timer /></el-icon>
              累计服务时长：<b style="color:#409EFF">{{ myProfile.totalHours || 0 }}</b> 小时
            </div>
          </el-card>
        </el-col>

        <!-- 可报名活动 -->
        <el-col :span="14">
          <el-card class="chart-card" shadow="never">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span class="card-title"><el-icon><List /></el-icon> 正在报名中的活动</span>
                <el-button link type="primary" size="small" @click="$router.push('/homepage/activities')">查看全部 →</el-button>
              </div>
            </template>
            <div v-if="openActivities.length === 0">
              <el-empty description="暂无正在报名的活动" :image-size="80" />
            </div>
            <div
              v-for="act in openActivities.slice(0,4)"
              :key="act.id"
              class="act-row"
              @click="$router.push(`/homepage/activity/${act.id}`)"
            >
              <div class="act-info">
                <div class="act-title">{{ act.title }}</div>
                <div class="act-meta">截止 {{ fmt(act.endTime) }}</div>
              </div>
              <div class="act-right">
                <el-progress
                  type="circle"
                  :percentage="Math.min(100,Math.round(act.joinedQuota/act.totalQuota*100))"
                  :width="50" :stroke-width="5"
                  :color="act.remainQuota===0?'#f56c6c':'#67c23a'"
                />
                <div style="font-size:11px;color:#909399;text-align:center;margin-top:4px">
                  剩{{ act.remainQuota }}名
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useAuthStore } from '@/utils/auth'
import { getActivityList, getMyActivities } from '@/api/activity'
import { getMyProfile } from '@/api/profile'
import { predictChurnRisk } from '@/api/ai'
import {
  List, Setting, PieChart, Menu, Clock, Medal, Timer,
  Connection, ChatLineRound, User, Trophy, Star
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const isAdmin = computed(() => authStore.userInfo?.role === 0)
const isOrganizer = computed(() => authStore.userInfo?.role === 2)

const pieChartRef = ref(null)
const recentActivities = ref([])
const openActivities = ref([])
const myOrgActivities = ref([])
const myProfile = ref({ skills: [], totalHours: 0 })
const highRiskCount = ref(0)

// 统计数据
const actStats = ref({ total: 0, open: 0, running: 0, done: 0 })
const orgStats = ref({ total: 0, open: 0, running: 0, done: 0 })

const orgStatCards = computed(() => [
  { label:'我的活动', value: orgStats.value.total,   color:'#409EFF', icon: 'List' },
  { label:'报名中',   value: orgStats.value.open,    color:'#67c23a', icon: 'Star' },
  { label:'进行中',   value: orgStats.value.running, color:'#e6a23c', icon: 'Timer' },
  { label:'已结束',   value: orgStats.value.done,    color:'#909399', icon: 'Trophy' },
])

const today = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}年${d.getMonth()+1}月${d.getDate()}日`
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6)  return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const statusType = (s) => ({ 0:'info', 1:'success', 2:'primary', 3:'danger' }[s] || 'info')
const fmt = (dt) => dt ? dt.replace('T',' ').substring(0,16) : '-'

const statCards = computed(() => [
  { label:'活动总数', value: actStats.value.total,   color:'#409EFF', icon: 'List' },
  { label:'报名中',   value: actStats.value.open,    color:'#67c23a', icon: 'Star' },
  { label:'进行中',   value: actStats.value.running, color:'#e6a23c', icon: 'Timer' },
  { label:'高风险志愿者', value: highRiskCount.value, color:'#f56c6c', icon: 'Trophy', link: '/homepage/admin/ai' },
])

const adminShortcuts = [
  { title:'活动管理', desc:'增删改查、状态流转', path:'/homepage/admin/activities', icon:'Setting', color:'#409EFF' },
  { title:'AI运营中心', desc:'流失预警、智能推荐', path:'/homepage/admin/ai',        icon:'Connection', color:'#67c23a' },
  { title:'智能查询', desc:'自然语言查询数据', path:'/homepage/admin/nl2sql',     icon:'ChatLineRound', color:'#e6a23c' },
  { title:'个人中心', desc:'修改信息与密码',   path:'/homepage/profile',           icon:'User', color:'#9b59b6' },
]

const initPieChart = (data) => {
  if (!pieChartRef.value) return
  const chart = echarts.init(pieChartRef.value)
  chart.setOption({
    tooltip: { trigger:'item', formatter:'{b}: {c} ({d}%)' },
    legend: { bottom:0, textStyle:{ fontSize:12 } },
    series: [{
      type:'pie', radius:['40%','70%'], center:['50%','45%'],
      data,
      label: { fontSize:12 },
      itemStyle: { borderRadius:6 }
    }]
  })
}

onMounted(async () => {
  try {
    const res = await getActivityList({ page:1, size:50 })
    const rows = res.data?.rows || []
    recentActivities.value = rows.slice(0,5)
    openActivities.value = rows.filter(r => r.status === 1)
    actStats.value = {
      total:   rows.length,
      open:    rows.filter(r => r.status===1).length,
      running: rows.filter(r => r.status===2).length,
      done:    rows.filter(r => r.status===3).length,
    }

    if (isAdmin.value) {
      await nextTick()
      initPieChart([
        { name:'未开始', value: rows.filter(r=>r.status===0).length, itemStyle:{color:'#909399'} },
        { name:'报名中', value: actStats.value.open,    itemStyle:{color:'#67c23a'} },
        { name:'进行中', value: actStats.value.running, itemStyle:{color:'#409EFF'} },
        { name:'已结束', value: actStats.value.done,    itemStyle:{color:'#f56c6c'} },
      ])
      // 异步加载高风险人数（不阻塞主流程）
      predictChurnRisk().then(r => {
        highRiskCount.value = (r.data || []).filter(v => v.riskLevel === '高').length
      }).catch(() => {})
    }
  } catch(e) {}

  if (isOrganizer.value) {
    try {
      const orgRes = await getMyActivities({ page:1, size:50 })
      const rows = orgRes.data?.rows || []
      myOrgActivities.value = rows
      orgStats.value = {
        total:   rows.length,
        open:    rows.filter(r => r.status===1).length,
        running: rows.filter(r => r.status===2).length,
        done:    rows.filter(r => r.status===3).length,
      }
    } catch(e) {}
  }

  if (!isAdmin.value && !isOrganizer.value) {
    try {
      const profileRes = await getMyProfile()
      myProfile.value = profileRes.data || { skills:[], totalHours:0 }
    } catch(e) {}
  }
})
</script>

<style scoped>
.dashboard { }

.welcome-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #1a73e8, #0d47a1);
  border-radius: 12px;
  padding: 24px 28px;
  margin-bottom: 20px;
  color: #fff;
}
.welcome-greeting {
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 8px;
}
.welcome-sub {
  font-size: 13px;
  opacity: 0.85;
  display: flex;
  align-items: center;
}

.stat-card {
  background: #fff;
  border-radius: 10px;
  border-left: 4px solid;
  padding: 20px 16px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: transform .2s;
}
.stat-card:hover { transform: translateY(-3px); }
.stat-num { font-size: 32px; font-weight: bold; margin: 6px 0 4px; }
.stat-label { font-size: 13px; color: #606266; }

.chart-card { border-radius: 10px; }
.card-title {
  font-size: 14px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}

.shortcut-card {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 20px;
  text-align: center;
  cursor: pointer;
  margin-bottom: 12px;
  transition: all .2s;
  border: 1px solid #e4e7ed;
}
.shortcut-card:hover {
  background: #ecf5ff;
  border-color: #409EFF;
  transform: translateY(-2px);
}
.shortcut-title { font-size: 14px; font-weight: bold; color: #303133; margin: 8px 0 4px; }
.shortcut-desc  { font-size: 12px; color: #909399; }

.act-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background .15s;
  border-radius: 6px;
  padding: 8px 10px;
}
.act-row:hover { background: #f5f7ff; }
.act-title { font-size: 14px; font-weight: bold; color: #303133; }
.act-meta  { font-size: 12px; color: #909399; margin-top: 3px; }
.act-right { display: flex; flex-direction: column; align-items: center; }
</style>
