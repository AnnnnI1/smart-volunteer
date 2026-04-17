<template>
  <div v-loading="loading">
    <el-button @click="$router.back()" style="margin-bottom:16px" plain>
      <el-icon><ArrowLeft /></el-icon> 返回
    </el-button>

    <el-card v-if="activity" class="detail-card" shadow="never">
      <!-- 标题区 -->
      <div class="detail-header">
        <div>
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:12px">
            <span style="font-size:12px;color:#909399;white-space:nowrap">活动阶段</span>
            <el-tag :type="statusType(activity.status)" size="large" effect="dark">
              {{ activity.statusDesc }}
            </el-tag>
          </div>
          <h2 class="detail-title">{{ activity.title }}</h2>
          <p class="detail-desc">{{ activity.description || '暂无描述' }}</p>
        </div>

        <!-- 报名操作 -->
        <div class="action-box">
          <div class="action-box-title">我的报名</div>
          <div class="quota-big">
            <span class="quota-remain">{{ activity.remainQuota }}</span>
            <span class="quota-label"> / {{ activity.totalQuota }} 剩余名额</span>
          </div>
          <el-progress
            :percentage="quotaPercent"
            :color="quotaColor"
            :stroke-width="12"
            style="margin: 12px 0"
          />

          <template v-if="activity.status === 1">
            <template v-if="!myReg">
              <el-alert
                v-if="myCancelledReg"
                title="您已取消本活动的报名，可重新报名"
                type="info"
                :closable="false"
                style="margin-bottom:10px"
              />
              <el-button
                type="primary"
                size="large"
                :disabled="activity.remainQuota <= 0"
                :loading="actionLoading"
                @click="handleRegister"
                style="width:100%"
              >
                {{ activity.remainQuota <= 0 ? '名额已满' : '立即报名' }}
              </el-button>
            </template>
            <template v-else>
              <el-alert
                :title="'报名状态：' + regStatusText(myReg.status)"
                :type="myReg.status === 0 ? 'success' : 'info'"
                :closable="false"
                style="margin-bottom:10px"
              />
              <el-button
                v-if="myReg.status === 0"
                type="danger"
                plain
                size="large"
                :loading="actionLoading"
                @click="handleCancel"
                style="width:100%"
              >取消报名</el-button>
            </template>
          </template>

          <!-- 进行中：志愿者输入活动签到码 -->
          <template v-else-if="activity.status === 2 && myReg?.status === 0">
            <el-alert
              title="活动进行中，请输入现场签到码完成签到"
              type="warning"
              :closable="false"
              style="margin-bottom:10px"
            />
            <el-input
              v-model="checkinInput"
              placeholder="请输入 6 位签到码"
              maxlength="6"
              style="margin-bottom:10px;font-size:20px;letter-spacing:6px;text-align:center"
              @keyup.enter="handleCheckin"
            />
            <el-button
              type="primary"
              size="large"
              :loading="actionLoading"
              :disabled="checkinInput.length !== 6"
              @click="handleCheckin"
              style="width:100%"
            >提交签到</el-button>
          </template>
          <!-- 已签到 -->
          <template v-else-if="activity.status === 2 && myReg?.status === 2">
            <el-alert title="已签到 ✓" type="success" :closable="false" />
          </template>

          <el-button v-else size="large" disabled style="width:100%">{{ activity.statusDesc }}</el-button>
        </div>
      </div>

      <el-divider />

      <!-- 详情信息 -->
      <el-descriptions :column="2" border>
        <el-descriptions-item label="活动ID">{{ activity.id }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusType(activity.status)">{{ activity.statusDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(activity.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDate(activity.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="总名额">{{ activity.totalQuota }}</el-descriptions-item>
        <el-descriptions-item label="已报名">{{ activity.joinedQuota }}</el-descriptions-item>
        <el-descriptions-item label="剩余名额（实时）">
          <span style="color:#67c23a;font-weight:bold">{{ activity.remainQuota }}</span>
          <el-tag size="small" type="success" style="margin-left:8px">Redis 实时</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ activity.organizerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(activity.createTime) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getActivityDetail, registerActivity, cancelRegistration, getMyRegistrations, submitCheckin } from '@/api/activity'
import { getCreditBalance } from '@/api/credit'

const route = useRoute()
const loading = ref(false)
const actionLoading = ref(false)
const activity = ref(null)
const myReg = ref(null)
const myCancelledReg = ref(null)
const checkinInput = ref('')

const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'primary', 3: 'danger' }[s] || 'info')
const regStatusText = (s) => ({ 0: '已报名', 1: '已取消', 2: '已签到✓', 4: '已缺席' }[s] || '')

const quotaPercent = computed(() => {
  if (!activity.value?.totalQuota) return 100
  return Math.min(100, Math.round((activity.value.joinedQuota / activity.value.totalQuota) * 100))
})

const quotaColor = computed(() => {
  const p = quotaPercent.value
  if (p >= 90) return '#f56c6c'
  if (p >= 60) return '#e6a23c'
  return '#67c23a'
})

const formatDate = (dt) => dt ? dt.replace('T', ' ').substring(0, 16) : '-'

const fetchData = async () => {
  loading.value = true
  try {
    const id = route.params.id
    const [detailRes, regRes] = await Promise.all([
      getActivityDetail(id),
      getMyRegistrations({ page: 1, size: 100 })
    ])
    activity.value = detailRes.data
    const regs = regRes.data?.rows || []
    myReg.value          = regs.find(r => r.activityId === Number(id) && r.status !== 1) || null
    myCancelledReg.value = regs.find(r => r.activityId === Number(id) && r.status === 1) || null
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  await ElMessageBox.confirm(`确定报名「${activity.value.title}」？`, '确认', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'info'
  })
  actionLoading.value = true
  try {
    await registerActivity(route.params.id)
    ElMessage.success('报名成功！')
    fetchData()
  } finally {
    actionLoading.value = false
  }
}

const handleCancel = async () => {
  await ElMessageBox.confirm('确定取消报名？', '提示', {
    confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
  })
  actionLoading.value = true
  try {
    await cancelRegistration(route.params.id)
    ElMessage.success('已取消报名')
    fetchData()
  } finally {
    actionLoading.value = false
  }
}

const handleCheckin = async () => {
  const code = checkinInput.value.trim()
  if (!code || code.length !== 6) { ElMessage.warning('请输入6位签到码'); return }
  actionLoading.value = true
  try {
    // 记录签到前积分，用于签到后比对提示
    let balanceBefore = 0
    try {
      const balRes = await getCreditBalance()
      balanceBefore = balRes.data?.balance ?? 0
    } catch (e) {}

    await submitCheckin(route.params.id, code)
    ElMessage.success('签到成功！')
    checkinInput.value = ''
    fetchData()

    // 2秒后轮询积分，提示到账
    setTimeout(async () => {
      try {
        const balRes = await getCreditBalance()
        const balanceAfter = balRes.data?.balance ?? 0
        const gained = balanceAfter - balanceBefore
        if (gained > 0) {
          ElNotification({ title: '积分到账', message: `签到奖励 +${gained} 积分，当前共 ${balanceAfter} 分`, type: 'success', duration: 4000 })
        }
      } catch (e) {}
    }, 2500)
  } finally {
    actionLoading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.detail-card { border-radius: 10px; }

.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 40px;
}

.detail-title {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
  margin: 8px 0;
}

.detail-desc {
  color: #606266;
  line-height: 1.6;
  max-width: 600px;
}

.action-box {
  flex-shrink: 0;
  width: 240px;
  background: #f8f9fa;
  border-radius: 10px;
  padding: 20px;
}

.action-box-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}

.quota-big {
  text-align: center;
}

.quota-remain {
  font-size: 42px;
  font-weight: bold;
  color: #409EFF;
}

.quota-label {
  font-size: 14px;
  color: #606266;
}
</style>
