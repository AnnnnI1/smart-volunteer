<template>
  <div v-loading="loading">
    <el-button @click="$router.back()" style="margin-bottom:16px" plain>
      <el-icon><ArrowLeft /></el-icon> 返回
    </el-button>

    <el-card v-if="activity" class="detail-card" shadow="never">
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

      <el-descriptions :column="2" border>
        <el-descriptions-item label="活动ID">{{ activity.id }}</el-descriptions-item>
        <el-descriptions-item label="当前状态">
          <el-tag :type="statusType(activity.status)">{{ activity.statusDesc }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatDate(activity.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatDate(activity.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="总名额">{{ activity.totalQuota }}</el-descriptions-item>
        <el-descriptions-item label="已报名">
          {{ activity.joinedQuota }}
          <el-button
            v-if="activity.joinedQuota > 0"
            type="primary"
            link
            size="small"
            style="margin-left:8px"
            @click="openParticipantDialog"
          >查看名单</el-button>
        </el-descriptions-item>
        <el-descriptions-item label="剩余名额（实时）">
          <span style="color:#67c23a;font-weight:bold">{{ activity.remainQuota }}</span>
          <el-tag size="small" type="success" style="margin-left:8px">Redis 实时</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发起人">{{ activity.organizerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(activity.createTime) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 举报入口 -->
      <div style="margin-top:16px;display:flex;justify-content:flex-end;align-items:center;gap:8px">
        <span v-if="activity.reportableUntil && new Date(activity.reportableUntil) < new Date()" style="color:#909399;font-size:13px">
          该活动已过举报有效期
        </span>
        <template v-else-if="activity.reportableUntil || activity.status >= 2">
          <el-button type="danger" plain size="small" @click="openReportDialog">
            <el-icon><Warning /></el-icon>&nbsp;举报活动
          </el-button>
        </template>
      </div>
    </el-card>

    <!-- 举报弹窗 -->
    <el-dialog v-model="reportDialogVisible" title="举报活动" width="540px" :close-on-click-modal="false">
      <el-alert
        title="请选择举报类型并详细描述问题。我们将认真核实每一条举报。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
      />
      <el-form :model="reportForm" label-width="90px" :rules="reportRules" ref="reportFormRef">
        <el-form-item label="举报类型" prop="reportType">
          <el-select v-model="reportForm.reportType" placeholder="请选择举报类型" style="width:100%">
            <el-option label="虚假活动" value="fake_activity" />
            <el-option label="欺诈收费" value="fraud_charge" />
            <el-option label="骚扰虐待" value="harassment" />
            <el-option label="违规内容" value="violation_content" />
            <el-option label="其他问题" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明" prop="description">
          <el-input
            v-model="reportForm.description"
            type="textarea"
            :rows="4"
            placeholder="请详细描述举报原因及提供相关证据线索（至少10个字）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="证据链接">
          <el-input
            v-model="evidenceInput"
            placeholder="可填写图片或文件链接（可选），多个用换行分隔"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="reportSubmitError"
        :title="reportSubmitError"
        type="error"
        :closable="false"
        show-icon
        style="margin-top:10px"
      />
      <template #footer>
        <el-button @click="reportDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="reportSubmitting" @click="handleSubmitReport">提交举报</el-button>
      </template>
    </el-dialog>

    <!-- 参与者名单弹窗 -->
    <el-dialog v-model="participantDialogVisible" title="参与者名单" width="640px">
      <el-table :data="participantList" v-loading="participantLoading" border stripe size="small" max-height="400">
        <el-table-column prop="nickname" label="昵称" width="100" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="regStatusTagType(row.status)" size="small">{{ regStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" min-width="140">
          <template #default="{ row }">{{ formatDate(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button
              type="danger"
              link
              size="small"
              @click="openVolunteerReport(row)"
            >举报</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 举报志愿者弹窗 -->
    <el-dialog v-model="volunteerReportDialogVisible" title="举报志愿者" width="500px" :close-on-click-modal="false">
      <el-alert
        title="请选择举报原因并详细描述问题。举报提交后管理员将进行核实处理。"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom:16px"
      />
      <el-form :model="volunteerReportForm" label-width="90px" ref="volunteerReportFormRef">
        <el-form-item label="被举报人">
          <span style="font-weight:bold">{{ volunteerReportForm.reportedNickname }}</span>
          <span style="color:#909399;margin-left:8px">ID: {{ volunteerReportForm.reportedUserId }}</span>
        </el-form-item>
        <el-form-item label="举报原因" prop="reportType">
          <el-select v-model="volunteerReportForm.reportType" placeholder="请选择举报原因" style="width:100%">
            <el-option label="无故缺席" value="no_show" />
            <el-option label="恶意破坏" value="malicious_damage" />
            <el-option label="骚扰他人" value="harassment" />
            <el-option label="违反规定" value="rule_violation" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="详细说明" prop="description">
          <el-input
            v-model="volunteerReportForm.description"
            type="textarea"
            :rows="4"
            placeholder="请详细描述该志愿者的违规行为（至少10个字）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="证据链接">
          <el-input
            v-model="volunteerReportForm.evidenceInput"
            placeholder="可填写图片或文件链接（可选），多个用换行分隔"
            type="textarea"
            :rows="2"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="volunteerReportDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="volunteerReportSubmitting" @click="handleVolunteerReport">提交举报</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { ArrowLeft, Warning } from '@element-plus/icons-vue'
import { getActivityDetail, registerActivity, cancelRegistration, getMyRegistrations, submitCheckin, getActivityRegistrations } from '@/api/activity'
import { getCreditBalance } from '@/api/credit'
import { submitReport } from '@/api/report'
import { useAuthStore } from '@/utils/auth'

const route = useRoute()
const loading = ref(false)
const actionLoading = ref(false)
const activity = ref(null)
const myReg = ref(null)
const myCancelledReg = ref(null)
const checkinInput = ref('')
const authStore = useAuthStore()

// ── 举报 ───────────────────────────────────────────────────────────
const reportDialogVisible = ref(false)
const reportSubmitting = ref(false)
const reportSubmitError = ref('')
const evidenceInput = ref('')
const reportFormRef = ref(null)
const reportForm = reactive({
  reportType: '',
  description: ''
})
const reportRules = {
  reportType: [{ required: true, message: '请选择举报类型' }],
  description: [
    { required: true, message: '请填写详细说明' },
    { min: 10, message: '详细说明至少10个字' }
  ]
}

const openReportDialog = () => {
  reportForm.reportType = ''
  reportForm.description = ''
  evidenceInput.value = ''
  reportSubmitError.value = ''
  reportDialogVisible.value = true
}

const handleSubmitReport = async () => {
  await reportFormRef.value.validate()
  reportSubmitting.value = true
  reportSubmitError.value = ''
  try {
    const evidenceUrls = evidenceInput.value
      ? evidenceInput.value.split('\n').map(s => s.trim()).filter(Boolean)
      : []
    await submitReport({
      reportType: reportForm.reportType,
      categoryCode: reportForm.reportType,
      activityId: activity.value.id,
      reportedUserId: activity.value.organizerId || null,
      description: reportForm.description,
      evidenceUrls
    })
    ElMessage.success('举报已提交，管理员将尽快处理')
    reportDialogVisible.value = false
  } catch (e) {
    reportSubmitError.value = e?.msg || e?.message || '提交失败，请稍后重试'
  } finally {
    reportSubmitting.value = false
  }
}

// 参与者名单
const participantDialogVisible = ref(false)
const participantList = ref([])
const participantLoading = ref(false)

const openParticipantDialog = async () => {
  participantDialogVisible.value = true
  participantLoading.value = true
  try {
    const res = await getActivityRegistrations(route.params.id)
    participantList.value = res.data || []
  } finally {
    participantLoading.value = false
  }
}

const regStatusTagType = (s) => ({ 0: 'success', 1: 'info', 2: 'primary' }[s] || 'info')

// 举报志愿者
const volunteerReportDialogVisible = ref(false)
const volunteerReportSubmitting = ref(false)
const volunteerReportFormRef = ref(null)
const volunteerReportForm = reactive({
  reportedUserId: null,
  reportedNickname: '',
  activityId: null,
  reportType: '',
  description: '',
  evidenceInput: ''
})

const openVolunteerReport = (reg) => {
  volunteerReportForm.reportedUserId = reg.userId
  volunteerReportForm.reportedNickname = reg.nickname || reg.username
  volunteerReportForm.activityId = activity.value.id
  volunteerReportForm.reportType = ''
  volunteerReportForm.description = ''
  volunteerReportForm.evidenceInput = ''
  volunteerReportDialogVisible.value = true
}

const handleVolunteerReport = async () => {
  if (!volunteerReportForm.reportType) {
    ElMessage.warning('请选择举报原因')
    return
  }
  if (!volunteerReportForm.description || volunteerReportForm.description.length < 10) {
    ElMessage.warning('请填写详细说明（至少10个字）')
    return
  }
  const evidenceUrls = volunteerReportForm.evidenceInput
    ? volunteerReportForm.evidenceInput.split('\n').filter(s => s.trim())
    : []
  volunteerReportSubmitting.value = true
  try {
    await submitReport({
      reportType: 'VR',
      categoryCode: volunteerReportForm.reportType,
      activityId: volunteerReportForm.activityId,
      reportedUserId: volunteerReportForm.reportedUserId,
      description: volunteerReportForm.description,
      evidenceUrls
    })
    ElMessage.success('举报已提交，管理员将尽快核实处理')
    volunteerReportDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '提交失败')
  } finally {
    volunteerReportSubmitting.value = false
  }
}

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
