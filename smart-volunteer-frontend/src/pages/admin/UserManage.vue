<template>
  <div>
    <!-- 全部用户视图 -->
    <el-card v-if="activeTab === 'all'" class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><el-icon><UserFilled /></el-icon> 用户管理</span>
          <el-badge :value="applyCount" :hidden="applyCount === 0" type="danger">
            <el-button size="small" @click="switchTab('apply')">待升级申请</el-button>
          </el-badge>
        </div>
      </template>

      <el-radio-group v-model="filterRole" @change="fetchList" style="margin-bottom:16px">
        <el-radio-button :value="null">全部</el-radio-button>
        <el-radio-button :value="1">志愿者</el-radio-button>
        <el-radio-button :value="2">组织者</el-radio-button>
      </el-radio-group>

      <el-table :data="list" v-loading="loading" border stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="积分" width="70">
          <template #default="{ row }">
            <span style="color:#e6a23c;font-weight:bold">{{ row.creditBalance ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="报名/取消" width="90">
          <template #default="{ row }">
            <span style="color:#409EFF">{{ row.signupCount ?? 0 }}</span>
            <span style="color:#909399"> / </span>
            <span style="color:#f56c6c">{{ row.cancelCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时长(h)" width="75">
          <template #default="{ row }">
            <span style="color:#67c23a;font-weight:bold">{{ row.totalHours ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="80">
          <template #default="{ row }">
            <el-tag :type="roleType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请" width="70">
          <template #default="{ row }">
            <el-tag v-if="row.applyOrganizer === 1" type="warning" size="small">待升级</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="65">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="155">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openDetailDialog(row)">详情</el-button>
            <!-- 有待审申请的志愿者：显示「查看申请」跳到申请详情 -->
            <el-button
              v-if="row.applyOrganizer === 1"
              type="warning"
              link
              size="small"
              @click="openApplyDrawer(row)"
            >查看申请</el-button>
            <!-- 组织者：只保留降级按钮 -->
            <el-button
              v-else-if="row.role === 2"
              type="info"
              link
              size="small"
              @click="changeRole(row, 1)"
            >降级</el-button>
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

    <!-- 待升级申请视图 -->
    <el-card v-else class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div style="display:flex;align-items:center;gap:12px">
            <el-button size="small" @click="switchTab('all')">
              <el-icon><ArrowLeft /></el-icon> 返回
            </el-button>
            <span class="card-title"><el-icon><UserFilled /></el-icon> 待升级申请</span>
          </div>
          <el-button size="small" @click="fetchApplyList" :loading="loading">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="以下志愿者已申请成为组织者，请点击「查看详情」查看AI尽调报告后进行审核"
        type="warning"
        :closable="false"
        style="margin-bottom:16px"
      />
      <el-empty v-if="!applyList.length && !loading" description="暂无待审核申请" />
      <el-table v-else :data="applyList" v-loading="loading" border stripe style="width:100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="积分" width="70">
          <template #default="{ row }">
            <span style="color:#e6a23c;font-weight:bold">{{ row.creditBalance ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="报名/取消" width="90">
          <template #default="{ row }">
            <span style="color:#409EFF">{{ row.signupCount ?? 0 }}</span>
            <span style="color:#909399"> / </span>
            <span style="color:#f56c6c">{{ row.cancelCount ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时长(h)" width="75">
          <template #default="{ row }">
            <span style="color:#67c23a;font-weight:bold">{{ row.totalHours ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.auditStatus === 0" type="warning" size="small">待审核</el-tag>
            <el-tag v-else-if="row.auditStatus === 1" type="success" size="small">已通过</el-tag>
            <el-tag v-else-if="row.auditStatus === 2" type="danger" size="small">已驳回</el-tag>
            <el-tag v-else type="warning" size="small">待审核</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="155">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;flex-wrap:nowrap;gap:2px">
              <el-button type="primary" link size="small" @click="openApplyDrawer(row)">查看详情</el-button>
              <template v-if="row.auditStatus === 0 || row.auditStatus === null || row.auditStatus === undefined">
                <el-button type="success" link size="small" @click="auditOrganizer(row, 1)">✓ 通过</el-button>
                <el-button type="danger" link size="small" @click="openRejectDialog(row)">✗ 驳回</el-button>
              </template>
              <span v-else style="color:#909399;font-size:12px">已审核</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 驳回原因对话框 -->
    <el-dialog v-model="rejectDialogVisible" title="驳回申请" width="420px">
      <el-form label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 申请详情侧抽屉 -->
    <el-drawer
      v-model="applyDrawerVisible"
      :title="applyDrawerRow ? `申请详情 — ${applyDrawerRow.nickname || applyDrawerRow.username}` : '申请详情'"
      size="520px"
      destroy-on-close
    >
      <template v-if="applyDrawerRow">
        <!-- 基本信息 -->
        <el-descriptions :column="2" border size="small" style="margin-bottom:20px">
          <el-descriptions-item label="用户ID">{{ applyDrawerRow.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ applyDrawerRow.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ applyDrawerRow.nickname || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ fmt(applyDrawerRow.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 志愿服务统计 -->
        <div class="drawer-section-title">志愿服务数据</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:20px">
          <div class="drawer-stat-card">
            <div class="drawer-stat-num" style="color:#e6a23c">{{ applyDrawerRow.creditBalance ?? 0 }}</div>
            <div class="drawer-stat-label">积分余额</div>
          </div>
          <div class="drawer-stat-card">
            <div class="drawer-stat-num" style="color:#409EFF">{{ applyDrawerRow.signupCount ?? 0 }}</div>
            <div class="drawer-stat-label">报名次数</div>
          </div>
          <div class="drawer-stat-card">
            <div class="drawer-stat-num" style="color:#f56c6c">{{ applyDrawerRow.cancelCount ?? 0 }}</div>
            <div class="drawer-stat-label">取消次数</div>
          </div>
          <div class="drawer-stat-card">
            <div class="drawer-stat-num" style="color:#67c23a">{{ applyDrawerRow.totalHours ?? 0 }}</div>
            <div class="drawer-stat-label">服务时长(h)</div>
          </div>
          <div class="drawer-stat-card" v-if="(applyDrawerRow.signupCount ?? 0) > 0">
            <div class="drawer-stat-num" style="color:#909399">
              {{ Math.round(((applyDrawerRow.signupCount ?? 0) - (applyDrawerRow.cancelCount ?? 0)) / (applyDrawerRow.signupCount ?? 1) * 100) }}%
            </div>
            <div class="drawer-stat-label">出勤率</div>
          </div>
        </div>

        <!-- 申请理由 -->
        <div class="drawer-section-title">申请理由</div>
        <el-card shadow="never" class="drawer-content-card" style="margin-bottom:16px">
          <p style="color:#303133;line-height:1.8;margin:0;white-space:pre-wrap">{{ applyDrawerRow.applyReason || '未填写申请理由' }}</p>
        </el-card>

        <!-- AI 尽调报告 -->
        <div class="drawer-section-title">AI 尽调报告</div>
        <el-card shadow="never" class="drawer-content-card drawer-ai-card">
          <template v-if="getAuditData(applyDrawerRow.aiAuditReport)">
            <!-- 三维度评分 -->
            <div style="display:flex;flex-direction:column;gap:14px">
              <div v-for="dim in getAuditDims(applyDrawerRow.aiAuditReport)" :key="dim.key">
                <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:4px">
                  <span style="font-size:13px;font-weight:bold;color:#303133">{{ dim.label }}</span>
                  <span style="font-size:13px;font-weight:bold" :style="{color: dim.color}">{{ dim.score }}分</span>
                </div>
                <el-progress :percentage="dim.score" :color="dim.color" :stroke-width="8" />
                <div style="font-size:12px;color:#606266;margin-top:4px">{{ dim.conclusion }}</div>
              </div>
            </div>
            <!-- 综合结论 -->
            <el-divider style="margin:16px 0" />
            <div style="display:flex;align-items:flex-start;gap:12px">
              <el-tag :type="getRiskTagType(applyDrawerRow.aiAuditReport)" effect="dark" style="flex-shrink:0">
                风险等级：{{ getRiskLevel(applyDrawerRow.aiAuditReport) }}
              </el-tag>
              <span style="color:#303133;font-size:13px;line-height:1.6">{{ getAiConclusion(applyDrawerRow.aiAuditReport) }}</span>
            </div>
          </template>
          <div v-else style="color:#909399;font-size:13px;text-align:center;padding:12px 0">
            AI尽调报告暂不可用（提交申请时AI服务未运行），请根据申请理由人工审核
          </div>
        </el-card>
      </template>

      <template #footer>
        <div style="display:flex;justify-content:flex-end;gap:10px">
          <el-button @click="applyDrawerVisible = false">关闭</el-button>
          <template v-if="applyDrawerRow && (applyDrawerRow.auditStatus === 0 || applyDrawerRow.auditStatus === null)">
            <el-button type="danger" @click="openRejectDialog(applyDrawerRow); applyDrawerVisible = false">✗ 驳回</el-button>
            <el-button type="success" @click="auditOrganizer(applyDrawerRow, 1); applyDrawerVisible = false">✓ 通过</el-button>
          </template>
          <span v-else style="color:#909399;font-size:12px;align-self:center">已审核</span>
        </div>
      </template>
    </el-drawer>

    <!-- 用户详情对话框 -->
    <el-dialog v-model="detailDialogVisible" :title="'用户详情 — ' + (detailRow?.nickname || detailRow?.username || '')" width="560px" destroy-on-close>
      <template v-if="detailRow">
        <!-- 基本信息 -->
        <div class="detail-section-title">基本信息</div>
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="用户ID">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detailRow.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ detailRow.nickname || '未设置' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag :type="roleType(detailRow.role)" size="small">{{ roleLabel(detailRow.role) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="账号状态">
            <el-tag :type="detailRow.status === 1 ? 'success' : 'danger'" size="small">
              {{ detailRow.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detailRow.email || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detailRow.phone || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ fmt(detailRow.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 活动统计 -->
        <div class="detail-section-title">活动统计</div>
        <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px">
          <div class="detail-stat-card">
            <div class="detail-stat-num" style="color:#e6a23c">{{ detailRow.creditBalance ?? 0 }}</div>
            <div class="detail-stat-label">积分余额</div>
          </div>
          <div class="detail-stat-card">
            <div class="detail-stat-num" style="color:#409EFF">{{ detailRow.signupCount ?? 0 }}</div>
            <div class="detail-stat-label">报名次数</div>
          </div>
          <div class="detail-stat-card">
            <div class="detail-stat-num" style="color:#f56c6c">{{ detailRow.cancelCount ?? 0 }}</div>
            <div class="detail-stat-label">取消次数</div>
          </div>
          <div class="detail-stat-card">
            <div class="detail-stat-num" style="color:#67c23a">{{ detailRow.totalHours ?? 0 }}</div>
            <div class="detail-stat-label">服务时长(h)</div>
          </div>
          <div class="detail-stat-card" v-if="(detailRow.signupCount ?? 0) > 0">
            <div class="detail-stat-num" style="color:#909399">
              {{ Math.round(((detailRow.signupCount ?? 0) - (detailRow.cancelCount ?? 0)) / (detailRow.signupCount ?? 1) * 100) }}%
            </div>
            <div class="detail-stat-label">出勤率</div>
          </div>
        </div>

        <!-- 组织者申请信息：仅 applyOrganizer=1 才显示 -->
        <template v-if="detailRow.applyOrganizer === 1">
          <div class="detail-section-title">组织者申请</div>
          <el-descriptions :column="1" border size="small" style="margin-bottom:12px">
            <el-descriptions-item label="申请状态">
              <el-tag v-if="detailRow.auditStatus === 1" type="success" size="small">已通过</el-tag>
              <el-tag v-else-if="detailRow.auditStatus === 2" type="danger" size="small">已驳回</el-tag>
              <el-tag v-else type="warning" size="small">待审核</el-tag>
            </el-descriptions-item>
            <el-descriptions-item v-if="detailRow.applyReason" label="申请理由">
              {{ detailRow.applyReason }}
            </el-descriptions-item>
          </el-descriptions>
        </template>
        <template v-else>
          <div class="detail-section-title">组织者申请</div>
          <p style="color:#909399;font-size:13px;margin:0 0 12px 0">该用户未申请成为组织者</p>
        </template>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <!-- 详情弹窗只保留降级功能，不提供升级（需走申请流程） -->
        <el-button v-if="detailRow?.role === 2" type="info" @click="changeRole(detailRow, 1); detailDialogVisible = false">降级为志愿者</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, ArrowLeft } from '@element-plus/icons-vue'
import { adminListUsers, adminUpdateUserRole, auditOrganizer as apiAuditOrganizer } from '@/api/user'

const loading    = ref(false)
const list       = ref([])
const applyList  = ref([])
const applyCount = ref(0)
const page       = ref(1)
const size       = ref(20)
const total      = ref(0)
const filterRole = ref(null)
const activeTab  = ref('all')

const rejectDialogVisible = ref(false)
const rejectReason        = ref('')
const currentRejectRow    = ref(null)

const detailDialogVisible = ref(false)
const detailRow           = ref(null)

const applyDrawerVisible  = ref(false)
const applyDrawerRow      = ref(null)

const openDetailDialog = (row) => {
  detailRow.value = row
  detailDialogVisible.value = true
}

const openApplyDrawer = (row) => {
  applyDrawerRow.value = row
  applyDrawerVisible.value = true
}

const roleType  = (r) => ({ 0: 'danger', 1: 'success', 2: 'warning' }[r] || 'info')
const roleLabel = (r) => ({ 0: '管理员', 1: '志愿者', 2: '组织者' }[r] || '未知')
const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : '-'

// AI 尽调报告解析
const getAuditData = (reportJson) => {
  try {
    if (!reportJson || reportJson === '{}' || reportJson === 'null') return null
    const data = JSON.parse(reportJson)
    return data.data || null
  } catch { return null }
}

const getAuditDims = (reportJson) => {
  const data = getAuditData(reportJson)
  if (!data || !data.dimensions_analysis) return []
  const dims = data.dimensions_analysis
  const dimMap = [
    { key: 'content_compliance', label: '内容合规', color: '#67c23a' },
    { key: 'history_fulfillment', label: '历史履约', color: '#409EFF' },
    { key: 'qualification_assessment', label: '资质评估', color: '#e6a23c' }
  ]
  return dimMap.map(d => {
    const dim = dims[d.key] || {}
    return { ...d, score: Math.round(dim.score || 0), conclusion: dim.conclusion || '-' }
  })
}

const getRiskLevel = (reportJson) => {
  const data = getAuditData(reportJson)
  return data?.overall_risk_level || '中'
}

const getAiConclusion = (reportJson) => {
  const data = getAuditData(reportJson)
  return data?.ai_conclusion || ''
}

const getRiskTagType = (reportJson) => {
  const level = getRiskLevel(reportJson)
  return level === '高' ? 'danger' : level === '中' ? 'warning' : 'success'
}

const fetchApplyList = async () => {
  loading.value = true
  try {
    const res = await adminListUsers({ applyOnly: true, page: 1, size: 100 })
    const rows = (res.data?.rows || []).filter(u => u.auditStatus === 0 || u.auditStatus === null || u.auditStatus === undefined)
    applyList.value  = rows
    applyCount.value = rows.length
  } finally {
    loading.value = false
  }
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filterRole.value !== null) params.role = filterRole.value
    const res = await adminListUsers(params)
    list.value  = res.data?.rows || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const switchTab = (tab) => {
  activeTab.value = tab
  if (tab === 'apply') fetchApplyList()
  else fetchList()
}

const changeRole = async (row, newRole) => {
  const label = newRole === 2 ? '升级为组织者' : '降级为志愿者'
  try {
    await ElMessageBox.confirm(
      `确定将「${row.nickname || row.username}」${label}？`,
      '确认操作',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    await adminUpdateUserRole(row.id, newRole)
    ElMessage.success(`操作成功，已${label}`)
    fetchApplyList()
    fetchList()
  } catch (e) {}
}

const auditOrganizer = async (row, auditStatus) => {
  const action = auditStatus === 1 ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(
      `确定${action}「${row.nickname || row.username}」的组织者申请？`,
      `审核${action}`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'success' }
    )
    await apiAuditOrganizer(row.id, auditStatus, null)
    ElMessage.success(`审核${action}成功`)
    fetchApplyList()
    fetchList()
  } catch (e) {}
}

const openRejectDialog = (row) => {
  currentRejectRow.value = row
  rejectReason.value = ''
  rejectDialogVisible.value = true
}

const confirmReject = async () => {
  if (!currentRejectRow.value) return
  await apiAuditOrganizer(currentRejectRow.value.id, 2, rejectReason.value || null)
  ElMessage.success('已驳回申请')
  rejectDialogVisible.value = false
  fetchApplyList()
}

onMounted(() => {
  fetchList()
  fetchApplyList()
})
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

/* 用户详情弹窗 */
.detail-section-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #409EFF;
}
.detail-stat-card {
  flex: 1;
  min-width: 80px;
  text-align: center;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 8px;
}
.detail-stat-num {
  font-size: 22px;
  font-weight: bold;
  line-height: 1;
  margin-bottom: 6px;
}
.detail-stat-label {
  font-size: 11px;
  color: #909399;
}

/* 申请详情抽屉 */
.drawer-section-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #409EFF;
}
.drawer-stat-card {
  flex: 1;
  min-width: 70px;
  text-align: center;
  background: #f5f7fa;
  border-radius: 8px;
  padding: 10px 6px;
}
.drawer-stat-num {
  font-size: 20px;
  font-weight: bold;
  line-height: 1;
  margin-bottom: 4px;
}
.drawer-stat-label {
  font-size: 11px;
  color: #909399;
}
.drawer-content-card {
  border-radius: 8px;
  background: #fafafa;
}
.drawer-ai-card {
  background: linear-gradient(135deg, #f0f7ff 0%, #fafafa 100%);
  border: 1px solid #d0e8ff;
}
</style>
