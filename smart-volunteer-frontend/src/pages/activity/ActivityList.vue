<template>
  <div>
    <!-- 搜索/筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <span class="page-title">
          <el-icon><List /></el-icon> 活动大厅
        </span>
        <div class="filter-right">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索活动名称/描述"
            clearable
            size="default"
            style="width:200px;margin-right:12px"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-radio-group v-model="filterStatus" @change="fetchActivities">
            <el-radio-button :value="null">全部</el-radio-button>
            <el-radio-button :value="0">未开始</el-radio-button>
            <el-radio-button :value="1">报名中</el-radio-button>
            <el-radio-button :value="2">进行中</el-radio-button>
            <el-radio-button :value="3">已结束</el-radio-button>
          </el-radio-group>
        </div>
      </div>
    </el-card>

    <!-- 活动卡片列表 -->
    <div v-loading="loading" class="activity-grid">
      <el-empty v-if="!loading && filteredActivities.length === 0" description="暂无活动" />

      <el-card
        v-for="act in filteredActivities"
        :key="act.id"
        class="activity-card"
        shadow="hover"
        @click="goDetail(act.id)"
      >
        <!-- 状态标签 -->
        <div class="card-header">
          <el-tag :type="statusType(act.status)" size="small" effect="dark">
            {{ act.statusDesc }}
          </el-tag>
          <div style="display:flex;align-items:center;gap:6px">
            <el-tag
              v-if="myRegMap[act.id] === 0"
              type="success" size="small" effect="plain"
            >已报名</el-tag>
            <el-tag
              v-else-if="myRegMap[act.id] === 1"
              type="info" size="small" effect="plain"
            >已取消</el-tag>
            <span class="card-time">{{ formatDate(act.startTime) }}</span>
          </div>
        </div>

        <h3 class="card-title">{{ act.title }}</h3>
        <p class="card-desc">{{ act.description || '暂无描述' }}</p>

        <!-- 名额进度条 -->
        <div class="quota-section">
          <div class="quota-label">
            <span>报名名额</span>
            <span class="quota-num">
              {{ act.remainQuota }}
              <span style="color:#999;font-size:12px"> / {{ act.totalQuota }}</span> 剩余
            </span>
          </div>
          <el-progress
            :percentage="quotaPercent(act)"
            :color="quotaColor(act)"
            :stroke-width="8"
            :show-text="false"
          />
        </div>

        <div class="card-footer">
          <template v-if="act.status === 1">
            <el-button
              v-if="myRegMap[act.id] === 0"
              type="danger"
              size="small"
              plain
              @click.stop="handleCancel(act.id)"
            >取消报名</el-button>
            <el-button
              v-else
              type="primary"
              size="small"
              :disabled="act.remainQuota <= 0"
              @click.stop="handleRegister(act)"
            >{{ myRegMap[act.id] === 1 ? '重新报名' : '立即报名' }}</el-button>
          </template>
          <el-button v-else size="small" disabled>{{ act.statusDesc }}</el-button>
          <span class="card-date">{{ formatDate(act.endTime) }} 截止</span>
        </div>
      </el-card>
    </div>

    <!-- 我的报名记录 -->
    <el-card class="my-reg-card" shadow="never">
      <template #header>
        <span>
          <el-icon><Document /></el-icon> 我的报名记录
        </span>
      </template>
        <el-table :data="myRegs" size="small" v-loading="regLoading">
        <el-table-column label="活动名称" min-width="160">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="goDetail(row.activityId)"
            >{{ row.activityTitle || `活动${row.activityId}` }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" width="140">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="regStatusType(row.status)" size="small">
              {{ regStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="个人签到码" width="110">
          <template #default="{ row }">
            <span v-if="row.checkinCode" style="font-family:monospace;font-size:14px;font-weight:bold;color:#409EFF;letter-spacing:2px">
              {{ row.checkinCode }}
            </span>
            <span v-else style="color:#c0c4cc;font-size:12px">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="danger"
              link
              size="small"
              @click="handleCancel(row.activityId)"
            >取消报名</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="regTotal > regSize"
        v-model:current-page="regPage"
        :page-size="regSize"
        :total="regTotal"
        layout="prev, pager, next"
        @current-change="fetchMyRegs"
        style="margin-top:12px;justify-content:center"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { List, Document, Search } from '@element-plus/icons-vue'
import { getActivityList, registerActivity, cancelRegistration, getMyRegistrations } from '@/api/activity'

const router = useRouter()
const loading = ref(false)
const regLoading = ref(false)
const filterStatus = ref(null)
const searchKeyword = ref('')
const activities = ref([])
const myRegs = ref([])
const myRegMap = ref({})   // activityId → status (0=已报名 1=已取消)
const regPage = ref(1)
const regSize = ref(10)
const regTotal = ref(0)

const filteredActivities = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return activities.value
  return activities.value.filter(a =>
    (a.title || '').toLowerCase().includes(kw) ||
    (a.description || '').toLowerCase().includes(kw)
  )
})

const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'primary', 3: 'danger' }[s] || 'info')
const regStatusType = (s) => ({ 0: 'primary', 1: 'info', 2: 'success' }[s] || 'info')
const regStatusText = (s) => ({ 0: '已报名', 1: '已取消', 2: '已完成' }[s] || '未知')

const quotaPercent = (act) => {
  if (!act.totalQuota) return 100
  return Math.round((act.joinedQuota / act.totalQuota) * 100)
}

const quotaColor = (act) => {
  const p = quotaPercent(act)
  if (p >= 90) return '#f56c6c'
  if (p >= 60) return '#e6a23c'
  return '#67c23a'
}

const formatDate = (dt) => {
  if (!dt) return '-'
  return dt.replace('T', ' ').substring(0, 16)
}

const fetchActivities = async () => {
  loading.value = true
  try {
    const params = { page: 1, size: 20 }
    if (filterStatus.value !== null) params.status = filterStatus.value
    const res = await getActivityList(params)
    activities.value = res.data?.rows || []
  } finally {
    loading.value = false
  }
}

const fetchMyRegs = async () => {
  regLoading.value = true
  try {
    const res = await getMyRegistrations({ page: regPage.value, size: regSize.value })
    myRegs.value = res.data?.rows || []
    regTotal.value = res.data?.total || 0
    // 全量拉取用于卡片显示（前100条足够）
    const allRes = await getMyRegistrations({ page: 1, size: 100 })
    const map = {}
    for (const r of (allRes.data?.rows || [])) {
      // 同一活动可能有多条记录（已取消后重新报名），取最新（已排序）
      if (!(r.activityId in map)) map[r.activityId] = r.status
    }
    myRegMap.value = map
  } finally {
    regLoading.value = false
  }
}

const goDetail = (id) => router.push(`/homepage/activity/${id}`)

const handleRegister = async (act) => {
  await ElMessageBox.confirm(`确定报名「${act.title}」？`, '确认报名', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  })
  try {
    await registerActivity(act.id)
    ElMessage.success('报名成功！')
    fetchActivities()
    fetchMyRegs()
  } catch (e) {}
}

const handleCancel = async (activityId) => {
  await ElMessageBox.confirm('确定取消报名？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  })
  try {
    await cancelRegistration(activityId)
    ElMessage.success('已取消报名')
    fetchActivities()
    fetchMyRegs()
  } catch (e) {}
}

onMounted(() => {
  fetchActivities()
  fetchMyRegs()
})
</script>

<style scoped>
.filter-card {
  margin-bottom: 16px;
  border-radius: 8px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.page-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  margin-bottom: 20px;
  min-height: 120px;
}

.activity-card {
  cursor: pointer;
  border-radius: 10px;
  transition: transform 0.2s;
}

.activity-card:hover {
  transform: translateY(-3px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.card-time {
  font-size: 12px;
  color: #909399;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin: 8px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  font-size: 13px;
  color: #606266;
  height: 38px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 12px;
}

.quota-section {
  margin-bottom: 12px;
}

.quota-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}

.quota-num {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.card-date {
  font-size: 12px;
  color: #909399;
}

.my-reg-card {
  border-radius: 8px;
}
/* v2 */
</style>
