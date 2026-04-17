<template>
  <div>
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><el-icon><Setting /></el-icon> 活动管理</span>
          <el-button type="primary" @click="openAdd">
            <el-icon><Plus /></el-icon> 新增活动
          </el-button>
        </div>
      </template>

      <!-- 状态筛选 -->
      <el-radio-group v-model="filterStatus" @change="fetchList" style="margin-bottom:16px">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="-1">
          <el-badge :value="pendingAuditCount" :hidden="pendingAuditCount === 0" type="danger">待审核</el-badge>
        </el-radio-button>
        <el-radio-button :value="0">未开始</el-radio-button>
        <el-radio-button :value="1">报名中</el-radio-button>
        <el-radio-button :value="2">进行中</el-radio-button>
        <el-radio-button :value="3">已结束</el-radio-button>
      </el-radio-group>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="活动标题" min-width="150" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.auditStatus === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.auditStatus === 2" type="danger" size="small">审核驳回</el-tag>
            <el-tag v-else :type="statusType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="名额" width="120">
          <template #default="{ row }">
            <span>{{ row.joinedQuota }}/{{ row.totalQuota }}</span>
            <span style="color:#67c23a;font-size:12px"> (剩{{ row.remainQuota }})</span>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="140">
          <template #default="{ row }">{{ fmt(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="140">
          <template #default="{ row }">{{ fmt(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="380" fixed="right">
          <template #default="{ row }">
            <!-- 待审核活动：管理员可手动通过 -->
            <el-button
              v-if="row.auditStatus === 0 && isAdminUser"
              type="success"
              link
              size="small"
              @click="manualApproveActivity(row)"
            >审核通过</el-button>
            <!-- 驳回状态：提示 -->
            <span v-else-if="row.auditStatus === 2" style="color:#f56c6c;font-size:12px;margin-right:6px">已驳回</span>
            <!-- 状态推进（仅已审核通过的活动） -->
            <el-button
              v-else-if="row.auditStatus === 1 && row.status < 3"
              type="success"
              link
              size="small"
              @click="nextStatus(row)"
            >{{ nextStatusLabel(row.status) }}</el-button>
            <!-- 签到码按钮（仅进行中） -->
            <el-button
              v-if="row.auditStatus === 1 && row.status === 2"
              type="warning"
              link
              size="small"
              @click="showCheckinCode(row)"
            >签到码</el-button>
            <!-- 查看报名（仅报名中/进行中/已结束） -->
            <el-button
              v-if="row.auditStatus === 1 && row.status >= 1"
              type="info"
              link
              size="small"
              @click="showRegistrations(row)"
            >报名名单</el-button>
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确定删除该活动？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchList"
        style="margin-top:16px;justify-content:flex-end"
      />
    </el-card>

    <!-- 签到码展示对话框 -->
    <el-dialog v-model="checkinDialogVisible" title="活动签到码" width="420px" align-center @close="stopCheckinTimer">
      <div style="text-align:center;padding:16px 0">
        <div style="font-size:13px;color:#909399;margin-bottom:16px">
          将此签到码展示给现场志愿者，志愿者在活动详情页输入此码完成签到
        </div>

        <!-- 大字签到码 -->
        <div style="margin-bottom:12px">
          <div style="font-size:11px;color:#909399;margin-bottom:8px">当前签到码</div>
          <div v-if="checkinCodeLoading" style="padding:20px">
            <el-icon class="is-loading" style="font-size:32px;color:#409EFF"><Loading /></el-icon>
          </div>
          <div v-else style="font-size:64px;font-weight:bold;letter-spacing:10px;color:#409EFF;
                              background:#ecf5ff;border-radius:12px;padding:20px 0;user-select:all">
            {{ currentCheckinCode }}
          </div>
        </div>

        <!-- 倒计时进度条 -->
        <div style="margin-bottom:16px">
          <div style="display:flex;justify-content:space-between;font-size:12px;color:#909399;margin-bottom:4px">
            <span>签到码有效期</span>
            <span :style="{ color: countdown <= 10 ? '#f56c6c' : '#409EFF', fontWeight: 'bold' }">
              {{ countdown }} 秒
            </span>
          </div>
          <el-progress
            :percentage="Math.round((countdown / 60) * 100)"
            :color="countdown <= 10 ? '#f56c6c' : countdown <= 20 ? '#e6a23c' : '#409EFF'"
            :stroke-width="8"
            :show-text="false"
          />
          <div v-if="countdown <= 10" style="font-size:12px;color:#f56c6c;margin-top:4px">
            即将刷新…
          </div>
        </div>

        <el-button
          type="primary"
          plain
          size="small"
          :loading="checkinCodeLoading"
          @click="refreshCheckinCode"
        >
          立即刷新
        </el-button>
      </div>
      <template #footer>
        <el-button @click="checkinDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 报名名单对话框 -->
    <el-dialog v-model="regDialogVisible" :title="`报名名单 - ${regActivityTitle}`" width="760px">
      <div style="margin-bottom:12px;display:flex;justify-content:flex-end;gap:8px">
        <el-button size="small" @click="copyRegList">复制名单</el-button>
      </div>
      <el-table :data="regList" v-loading="regLoading" border stripe size="small" max-height="420">
        <el-table-column prop="nickname" label="昵称" width="100" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="regStatusType(row.status)" size="small">{{ regStatusDesc(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" min-width="140">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              type="primary"
              link
              size="small"
              @click="handleManualCheckin(row)"
            >手动签到</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="regDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑对话框 -->    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑活动' : '新增活动'" width="560px">      <el-form :model="form" ref="formRef" label-width="90px" :rules="rules">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入活动标题" />
        </el-form-item>
        <el-form-item label="活动描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入活动描述" />
        </el-form-item>
        <el-form-item label="所需技能">
          <div style="width:100%">
            <div style="margin-bottom:6px;display:flex;flex-wrap:wrap;gap:4px">
              <el-tag
                v-for="(s, i) in skillTagList"
                :key="i"
                closable
                @close="skillTagList.splice(i,1)"
                type="primary"
                effect="light"
              >{{ s }}</el-tag>
            </div>
            <div style="display:flex;gap:8px">
              <el-input v-model="newSkillInput" placeholder="输入技能后回车添加" size="small" @keyup.enter="addSkillTag" clearable />
              <el-button size="small" @click="addSkillTag">添加</el-button>
            </div>
            <div style="margin-top:6px">
              <el-tag
                v-for="s in presetSkills"
                :key="s"
                :type="skillTagList.includes(s) ? 'primary' : 'info'"
                effect="plain"
                size="small"
                style="cursor:pointer;margin:2px"
                @click="toggleSkillTag(s)"
              >{{ s }}</el-tag>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="总名额" prop="totalQuota">
          <el-input-number v-model="form.totalQuota" :min="1" :max="9999" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="form.startTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择开始时间"
            style="width:100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="form.endTime"
            type="datetime"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择结束时间"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, Plus, Loading } from '@element-plus/icons-vue'
import { useAuthStore } from '@/utils/auth'
import {
  getActivityList, addActivity, updateActivity,
  deleteActivity, updateActivityStatus, generateCheckinCode,
  getMyActivities, getActivityRegistrations, manualCheckin,
  adminApproveActivity
} from '@/api/activity'

const authStore = useAuthStore()
const isAdminUser = computed(() => authStore.userInfo?.role === 0)

const loading = ref(false)
const submitting = ref(false)
const list = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const filterStatus = ref(null)
const pendingAuditCount = computed(() => list.value.filter(a => a.auditStatus === 0).length)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  title: '',
  description: '',
  requiredSkills: '',
  totalQuota: 10,
  startTime: '',
  endTime: ''
})

const skillTagList = ref([])
const newSkillInput = ref('')
const presetSkills = ['医疗', '急救', '教育', '翻译', '社区服务', '文化', '体育', '法律', '心理', '环保']

const addSkillTag = () => {
  const s = newSkillInput.value.trim()
  if (s && !skillTagList.value.includes(s)) skillTagList.value.push(s)
  newSkillInput.value = ''
}
const toggleSkillTag = (s) => {
  const idx = skillTagList.value.indexOf(s)
  if (idx >= 0) skillTagList.value.splice(idx, 1)
  else skillTagList.value.push(s)
}

const rules = {
  title: [{ required: true, message: '请输入标题' }],
  totalQuota: [{ required: true, message: '请填写名额' }],
  startTime: [{ required: true, message: '请选择开始时间' }],
  endTime: [{ required: true, message: '请选择结束时间' }]
}

const statusType = (s) => ({ 0: 'info', 1: 'success', 2: 'primary', 3: 'danger' }[s] || 'info')
const nextStatusLabel = (s) => (['开放报名', '开始活动', '结束活动', ''][s] || '')
const fmt = (dt) => dt ? dt.replace('T', ' ').substring(0, 16) : '-'

const fetchList = async () => {
  loading.value = true
  try {
    const isOrganizer = authStore.userInfo?.role === 2
    const isAdmin = authStore.userInfo?.role === 0
    const params = { page: page.value, size: size.value }
    // filterStatus=-1 表示「待审核」，用 auditStatus 过滤，不传 status 参数
    if (filterStatus.value !== null && filterStatus.value !== -1) params.status = filterStatus.value
    // 管理员/组织者后台需要看到全量活动（含待审核），不过滤 auditStatus
    if (isAdmin || isOrganizer) params.includeAll = true
    const res = isOrganizer
      ? await getMyActivities(params)
      : await getActivityList(params)
    let rows = res.data?.rows || []
    // 前端过滤待审核
    if (filterStatus.value === -1) rows = rows.filter(a => a.auditStatus === 0)
    list.value = rows
    total.value = filterStatus.value === -1 ? rows.length : (res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const openAdd = () => {
  isEdit.value = false
  Object.assign(form, { id: null, title: '', description: '', requiredSkills: '', totalQuota: 10, startTime: '', endTime: '' })
  skillTagList.value = []
  dialogVisible.value = true
}

const openEdit = (row) => {
  isEdit.value = true
  Object.assign(form, row)
  skillTagList.value = row.requiredSkills ? row.requiredSkills.split(',').filter(Boolean) : []
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()
  form.requiredSkills = skillTagList.value.join(',')
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateActivity(form.id, form)
      ElMessage.success('修改成功')
    } else {
      const res = await addActivity(form)
      const d = res.data
      if (d && d.passed === false) {
        // AI 审核未通过或服务不可用
        ElMessage.warning(d.message || '活动已提交，等待审核')
      } else {
        ElMessage.success('活动发布成功，已通过AI风控审核')
      }
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (id) => {
  await deleteActivity(id)
  ElMessage.success('删除成功')
  fetchList()
}

const nextStatus = async (row) => {
  await updateActivityStatus(row.id, row.status + 1)
  ElMessage.success('状态已更新')
  fetchList()
}

// 管理员手动审核通过（AI服务不可用时人工复核）
const manualApproveActivity = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定人工审核通过「${row.title}」？审核通过后，活动将进入"已发布/未开始"状态，组织者需手动开放报名。`,
      '人工审核确认',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'warning' }
    )
    await adminApproveActivity(row.id)
    ElMessage.success('审核已通过，活动进入"未开始"状态，可开放报名')
    fetchList()
  } catch (e) {}
}

// 签到码
const checkinDialogVisible = ref(false)
const checkinCodeLoading   = ref(false)
const currentCheckinCode   = ref('')
const checkinActivityId    = ref(null)
const countdown            = ref(60)
let   checkinTimer         = null

const showCheckinCode = async (row) => {
  checkinActivityId.value = row.id
  checkinDialogVisible.value = true
  await refreshCheckinCode()
}

const refreshCheckinCode = async () => {
  stopCheckinTimer()
  checkinCodeLoading.value = true
  try {
    const res = await generateCheckinCode(checkinActivityId.value)
    currentCheckinCode.value = res.data?.code || ''
    countdown.value = 60
    startCheckinTimer()
  } finally {
    checkinCodeLoading.value = false
  }
}

const startCheckinTimer = () => {
  checkinTimer = setInterval(async () => {
    countdown.value--
    if (countdown.value <= 0) {
      await refreshCheckinCode()
    }
  }, 1000)
}

const stopCheckinTimer = () => {
  if (checkinTimer) {
    clearInterval(checkinTimer)
    checkinTimer = null
  }
}

// 报名名单
const regDialogVisible  = ref(false)
const regLoading        = ref(false)
const regList           = ref([])
const regActivityTitle  = ref('')
const regActivityId     = ref(null)
const regActivityStatus = ref(0)

const regStatusType = (s) => ({ 0: 'success', 1: 'info', 2: 'primary', 4: 'danger' }[s] || 'info')
const regStatusDesc = (s) => ({ 0: '已报名', 1: '已取消', 2: '已签到', 4: '已缺席' }[s] || '未知')

const showRegistrations = async (row) => {
  regActivityId.value     = row.id
  regActivityTitle.value  = row.title
  regActivityStatus.value = row.status
  regDialogVisible.value  = true
  regLoading.value = true
  try {
    const res = await getActivityRegistrations(row.id)
    regList.value = res.data || []
  } finally {
    regLoading.value = false
  }
}

const handleManualCheckin = async (reg) => {
  await manualCheckin(regActivityId.value, reg.userId)
  ElMessage.success(`已为 ${reg.nickname || reg.username} 手动签到`)
  // 刷新名单
  const res = await getActivityRegistrations(regActivityId.value)
  regList.value = res.data || []
}

const copyRegList = () => {
  const lines = regList.value.map(r =>
    `${r.nickname || r.username}\t${r.phone || '-'}\t${regStatusDesc(r.status)}`
  ).join('\n')
  navigator.clipboard.writeText(lines).then(() => ElMessage.success('名单已复制到剪贴板'))
}

onMounted(fetchList)
onUnmounted(stopCheckinTimer)
</script>

<style scoped>
.page-card { border-radius: 10px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
