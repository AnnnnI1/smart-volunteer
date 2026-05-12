<template>
  <div class="feedback-center">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="header-title">
          <span><el-icon><Warning /></el-icon> 负反馈中心</span>
        </div>
      </template>

      <!-- 统计卡片 -->
      <div class="stat-cards">
        <div class="stat-card" :class="{ 'stat-active': activeTab === '0' }">
          <div class="stat-num" style="color:#f56c6c">{{ stats.pending }}</div>
          <div class="stat-label">待受理</div>
          <div class="stat-tab-link" @click="activeTab = '0'">查看</div>
        </div>
        <div class="stat-card" :class="{ 'stat-active': activeTab === '1' }">
          <div class="stat-num" style="color:#e6a23c">{{ stats.processing }}</div>
          <div class="stat-label">处理中</div>
          <div class="stat-tab-link" @click="activeTab = '1'">查看</div>
        </div>
        <div class="stat-card" :class="{ 'stat-active': activeTab === '2' }">
          <div class="stat-num" style="color:#67c23a">{{ stats.resolved }}</div>
          <div class="stat-label">已结案</div>
          <div class="stat-tab-link" @click="activeTab = '2'">查看</div>
        </div>
        <div class="stat-card">
          <div class="stat-num" style="color:#909399">{{ stats.total }}</div>
          <div class="stat-label">累计举报</div>
        </div>
        <div class="stat-card">
          <div class="stat-num" style="color:#909399">{{ stats.invalid }}</div>
          <div class="stat-label">无效举报</div>
        </div>
      </div>

      <!-- Tab 切换 -->
      <el-tabs v-model="activeTab" @tab-change="onTabChange" style="margin-top:16px">
        <!-- Tab 1: 举报管理 -->
        <el-tab-pane label="举报管理" name="0">
          <!-- 筛选栏 -->
          <div class="filter-bar" style="margin-bottom:12px">
            <el-radio-group v-model="filterReportType" @change="fetchList" size="small">
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button :value="1">活动举报</el-radio-button>
              <el-radio-button :value="2">志愿者违规</el-radio-button>
            </el-radio-group>
          </div>

          <!-- 举报列表 -->
          <el-table :data="reportList" v-loading="loading" border stripe style="margin-top:12px"
            :max-height="tableMaxHeight">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="类型" width="90">
              <template #default="{ row }">
                <el-tag :type="row.reportType === 'AR' ? 'danger' : 'warning'" size="small">
                  {{ row.reportType === 'AR' ? '活动举报' : '志愿者违规' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="categoryCode" label="分类" width="140">
              <template #default="{ row }">
                <span style="font-size:12px;color:#606266">{{ row.categoryCodeName || getCategoryName(row.categoryCode) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="举报人" min-width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.reporterId)">
                  {{ row.reporterNickname || row.reporterUsername || '-' }}
                </el-button>
                <span v-if="row.reporterId" style="color:#909399;font-size:11px;display:block">ID: {{ row.reporterId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="被举报人" min-width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.reportedUserId)">
                  {{ row.reportedNickname || row.reportedUsername || '-' }}
                </el-button>
                <span v-if="row.reportedUserId" style="color:#909399;font-size:11px;display:block">ID: {{ row.reportedUserId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="关联活动" min-width="140">
              <template #default="{ row }">
                <template v-if="row.activityId">
                  <el-button link type="primary" size="small"
                    @click="goActivity(row.activityId)">
                    {{ row.activityTitle || ('#' + row.activityId) }}
                  </el-button>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="70">
              <template #default="{ row }">
                <el-tag :type="priorityType(row.priority)" size="small">{{ row.priorityDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.statusDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="AI分析" width="70">
              <template #default="{ row }">
                <el-tag v-if="row.aiAnalysis" type="success" size="small" effect="plain">有</el-tag>
                <el-tag v-else type="info" size="small" effect="plain">无</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="举报时间" width="140">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openReportDetail(row)">详情</el-button>
                <el-button v-if="row.status === 0" type="success" link size="small"
                  @click="handleAccept(row)">受理</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="page"
            :page-size="20"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="fetchList"
            style="margin-top:12px;justify-content:flex-end"
          />
        </el-tab-pane>

        <!-- Tab 2: 惩罚记录 -->
        <el-tab-pane label="惩罚记录" name="1">
          <el-table :data="penaltyList" v-loading="penaltyLoading" border stripe style="margin-top:12px"
            :max-height="tableMaxHeight">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="被惩罚用户" min-width="140">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.userId)">
                  {{ row.reportedNickname || row.reportedUsername || ('ID: ' + row.userId) }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="举报人" min-width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.reporterId)">
                  {{ row.reporterNickname || row.reporterUsername || '-' }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="penaltyType" label="惩罚类型" width="140">
              <template #default="{ row }">
                <el-tag size="small">{{ getPenaltyTypeName(row.penaltyType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="惩罚内容" min-width="200">
              <template #default="{ row }">
                <span style="font-size:12px;color:#606266">{{ row.penaltyContentDesc || row.penaltyValue || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="操作人" width="80">
              <template #default="{ row }">
                <span>{{ row.operatorId }}</span>
              </template>
            </el-table-column>
            <el-table-column label="关联举报" width="90">
              <template #default="{ row }">
                <el-button v-if="row.reportId" type="primary" link size="small" @click="goReport(row.reportId)">#{{ row.reportId }}</el-button>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="是否撤销" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.isReversed === 1" type="info" size="small">已撤销</el-tag>
                <el-button v-else type="warning" link size="small" @click="handleReversePenalty(row)">撤销</el-button>
              </template>
            </el-table-column>
            <el-table-column label="时间" width="140">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="penaltyPage"
            :page-size="20"
            :total="penaltyTotal"
            layout="total, prev, pager, next"
            @current-change="fetchPenalties"
            style="margin-top:12px;justify-content:flex-end"
          />
        </el-tab-pane>

        <!-- Tab 3: 信用管理 -->
        <el-tab-pane label="信用管理" name="2">
          <div class="filter-bar" style="margin-bottom:12px">
            <span style="color:#909399;font-size:12px">信用预警：以下用户信用分 &lt; 60</span>
          </div>
          <el-table :data="creditList" v-loading="creditLoading" border stripe
            :max-height="tableMaxHeight">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="昵称" min-width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.userId)">
                  {{ row.userNickname || row.userUsername || ('ID: ' + row.userId) }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="用户名" min-width="120">
              <template #default="{ row }">
                <span style="color:#909399">{{ row.userUsername || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.userRole === 0" type="danger" size="small">管理员</el-tag>
                <el-tag v-else-if="row.userRole === 1" type="primary" size="small">志愿者</el-tag>
                <el-tag v-else-if="row.userRole === 2" type="warning" size="small">组织者</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="信用分" width="100">
              <template #default="{ row }">
                <span style="font-weight:bold" :style="{ color: row.creditScore < 40 ? '#f56c6c' : row.creditScore < 60 ? '#e6a23c' : '#67c23a' }">
                  {{ row.creditScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="信用等级" width="80">
              <template #default="{ row }">
                <el-tag :type="creditLevelTagType(row.creditLevel)" size="small">{{ row.creditLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="违规次数" width="90">
              <template #default="{ row }">
                <span>{{ row.totalViolations }}</span>
              </template>
            </el-table-column>
            <el-table-column label="被举报次数" width="100">
              <template #default="{ row }">
                <span>{{ row.totalReports }}</span>
              </template>
            </el-table-column>
            <el-table-column label="组织者等级" width="100">
              <template #default="{ row }">
                <span>{{ getOrganizerLevelName(row.organizerLevel) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="封禁状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.banType === 1" type="warning" size="small">临时封禁</el-tag>
                <el-tag v-else-if="row.banType === 2" type="danger" size="small">永久封禁</el-tag>
                <span v-else style="color:#67c23a">正常</span>
              </template>
            </el-table-column>
            <el-table-column label="最近违规" width="140">
              <template #default="{ row }">{{ fmt(row.lastViolationAt) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="creditPage"
            :page-size="20"
            :total="creditTotal"
            layout="total, prev, pager, next"
            @current-change="fetchCreditScores"
            style="margin-top:12px;justify-content:flex-end"
          />
        </el-tab-pane>

        <!-- Tab 4: 申诉中心 -->
        <el-tab-pane label="申诉中心" name="3">
          <div class="filter-bar" style="margin-bottom:12px">
            <el-radio-group v-model="filterAppealDecision" @change="fetchAppeals" size="small">
              <el-radio-button :value="null">全部</el-radio-button>
              <el-radio-button :value="0">待处理</el-radio-button>
              <el-radio-button :value="1">维持原判</el-radio-button>
              <el-radio-button :value="2">撤销惩罚</el-radio-button>
            </el-radio-group>
          </div>
          <el-table :data="appealList" v-loading="appealLoading" border stripe :max-height="tableMaxHeight">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="举报" width="120">
              <template #default="{ row }">
                <span>#{{ row.reportId }}</span>
                <el-tag :type="row.reportType === 'AR' ? 'danger' : 'warning'" size="small" style="margin-left:4px">
                  {{ row.reportType === 'AR' ? '活动' : '违规' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="举报人" min-width="110">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.reporterId)">
                  {{ row.reporterNickname || row.reporterUsername || '-' }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="被举报人" min-width="110">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.reportedUserId)">
                  {{ row.reportedNickname || row.reportedUsername || '-' }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="申诉人" width="100">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.appellantId)">
                  {{ row.appellantNickname || row.appellantUsername || ('ID: ' + row.appellantId) }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column prop="appealReason" label="申诉理由" min-width="150" show-overflow-tooltip />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="appealDecisionTagType(row.decision)" size="small">{{ row.decisionDesc }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="adminResponse" label="管理员回复" min-width="150" show-overflow-tooltip />
            <el-table-column label="提交时间" width="140">
              <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.decision == null" type="primary" link size="small"
                  @click="openAppealDetail(row)">处理</el-button>
                <el-button v-else type="info" link size="small" @click="openAppealDetail(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="appealPage"
            :page-size="20"
            :total="appealTotal"
            layout="total, prev, pager, next"
            @current-change="fetchAppeals"
            style="margin-top:12px;justify-content:flex-end"
          />
        </el-tab-pane>

        <!-- Tab 5: 黑名单 -->
        <el-tab-pane label="黑名单" name="4">
          <el-table :data="blacklist" v-loading="blacklistLoading" border stripe
            :max-height="tableMaxHeight">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column label="昵称" min-width="120">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openUserDetail(row.userId)">
                  {{ row.userNickname || row.userUsername || ('ID: ' + row.userId) }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="用户名" min-width="120">
              <template #default="{ row }">
                <span style="color:#909399">{{ row.userUsername || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.userRole === 0" type="danger" size="small">管理员</el-tag>
                <el-tag v-else-if="row.userRole === 1" type="primary" size="small">志愿者</el-tag>
                <el-tag v-else-if="row.userRole === 2" type="warning" size="small">组织者</el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="信用分" width="80">
              <template #default="{ row }">
                <span style="color:#f56c6c;font-weight:bold">{{ row.creditScore }}</span>
              </template>
            </el-table-column>
            <el-table-column label="信用等级" width="80">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.creditLevel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="封禁类型" width="90">
              <template #default="{ row }">
                <el-tag v-if="row.banType === 1" type="warning" size="small">临时封禁</el-tag>
                <el-tag v-else-if="row.banType === 2" type="danger" size="small">永久封禁</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="banReason" label="封禁原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="封禁截止" width="140">
              <template #default="{ row }">{{ fmt(row.banUntil) }}</template>
            </el-table-column>
            <el-table-column label="最近违规" width="140">
              <template #default="{ row }">{{ fmt(row.lastViolationAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="success" link size="small" @click="handleForceUnban(row)">强制解封</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="blacklistPage"
            :page-size="20"
            :total="blacklistTotal"
            layout="total, prev, pager, next"
            @current-change="fetchBlacklist"
            style="margin-top:12px;justify-content:flex-end"
          />
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 举报详情弹窗 -->
    <ReportDetailDialog
      v-model="reportDetailVisible"
      :report-id="currentReportId"
      @refresh="fetchList"
    />
    <AppealDetailDialog
      v-model="appealDetailVisible"
      :appeal-id="currentAppealData?.id"
      :appeal-data="currentAppealData"
      @refresh="fetchAppeals"
      @open-report="handleOpenReportFromAppeal"
    />
    <!-- 用户详情弹窗 -->
    <UserDetailDialog
      v-model="userDetailVisible"
      :user-id="currentUserId"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning } from '@element-plus/icons-vue'
import {
  listReports, getReportStatistics,
  listPenaltyRecords, listCreditScores, listAppeals,
  listBlacklist, acceptReport, forceUnban, reversePenalty
} from '@/api/report'
import ReportDetailDialog from './ReportDetailDialog.vue'
import AppealDetailDialog from './AppealDetailDialog.vue'
import UserDetailDialog from './UserDetailDialog.vue'

const activeTab = ref('0')
const loading = ref(false)
const reportList = ref([])
const page = ref(1)
const total = ref(0)
const filterReportType = ref(null)
const reportDetailVisible = ref(false)
const currentReportId = ref(null)
const appealDetailVisible = ref(false)
const currentAppealData = ref(null)

const userDetailVisible = ref(false)
const currentUserId = ref(null)

const stats = reactive({ pending: 0, processing: 0, resolved: 0, invalid: 0, total: 0 })

const penaltyLoading = ref(false)
const penaltyList = ref([])
const penaltyPage = ref(1)
const penaltyTotal = ref(0)

const creditLoading = ref(false)
const creditList = ref([])
const creditPage = ref(1)
const creditTotal = ref(0)

const appealLoading = ref(false)
const appealList = ref([])
const appealPage = ref(1)
const appealTotal = ref(0)
const filterAppealDecision = ref(null)

const blacklistLoading = ref(false)
const blacklist = ref([])
const blacklistPage = ref(1)
const blacklistTotal = ref(0)

const tableMaxHeight = computed(() => {
  const vh = window.innerHeight
  return Math.max(300, vh - 400)
})

const CATEGORY_NAMES = {
  'AR-01': '活动内容与实际不符', 'AR-02': '存在安全隐患', 'AR-03': '组织者不当行为',
  'AR-04': '违规收集信息', 'AR-05': '虚假宣传', 'AR-06': '其他违规',
  'VR-01': '无故缺席', 'VR-02': '故意破坏秩序', 'VR-03': '言语攻击骚扰',
  'VR-04': '损坏公物', 'VR-05': '违反纪律', 'VR-06': '其他违规行为'
}
const getCategoryName = (code) => CATEGORY_NAMES[code] || code || '-'

const priorityType = (p) => ({ 0: 'info', 1: 'warning', 2: 'danger', 3: 'danger' }[p] || 'info')
const statusTagType = (s) => ({ 0: 'danger', 1: 'warning', 2: 'success', 3: 'info' }[s] || 'info')
const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').substring(0, 16) : '-'

const getPenaltyTypeName = (t) => ({
  'credit_deduct': '积分扣除', 'ban': '账号封禁', 'activity_limit': '限制发起活动',
  'demotion': '组织者降级', 'credit_recover': '积分恢复', 'unban': '解封'
}[t] || t || '-')

const getOrganizerLevelName = (l) => ({ 0: '新晋', 1: '铜牌', 2: '银牌', 3: '金牌' }[l] || '无' || '-')

const creditLevelTagType = (level) => {
  if (level === '优秀') return 'success'
  if (level === '良好') return 'primary'
  if (level === '关注') return 'warning'
  return 'danger'
}

const appealDecisionTagType = (d) => ({ 0: 'info', 1: 'success', 2: 'danger' }[d] || 'info')

const onTabChange = (tab) => {
  page.value = 1
  if (tab === '0') fetchList()
  else if (tab === '1') fetchPenalties()
  else if (tab === '2') fetchCreditScores()
  else if (tab === '3') fetchAppeals()
  else if (tab === '4') fetchBlacklist()
}

const fetchList = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: 20 }
    if (filterReportType.value !== null) params.reportType = filterReportType.value
    const res = await listReports(params)
    reportList.value = res.data?.rows || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try {
    const res = await getReportStatistics()
    Object.assign(stats, res.data || {})
  } catch (e) {}
}

const openReportDetail = (row) => {
  currentReportId.value = row.id
  reportDetailVisible.value = true
}

const handleAccept = async (row) => {
  try {
    await ElMessageBox.confirm('确定受理举报 #' + row.id + '？', '确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'info'
    })
    await acceptReport(row.id)
    ElMessage.success('已受理')
    fetchList()
    fetchStats()
  } catch (e) {}
}

const fetchPenalties = async () => {
  penaltyLoading.value = true
  try {
    const res = await listPenaltyRecords({ page: penaltyPage.value, size: 20 })
    penaltyList.value = res.data?.rows || []
    penaltyTotal.value = res.data?.total || 0
  } finally { penaltyLoading.value = false }
}

const fetchCreditScores = async () => {
  creditLoading.value = true
  try {
    const res = await listCreditScores({ page: creditPage.value, size: 20 })
    creditList.value = res.data?.rows || []
    creditTotal.value = res.data?.total || 0
  } finally { creditLoading.value = false }
}

const fetchAppeals = async () => {
  appealLoading.value = true
  try {
    const params = { page: appealPage.value, size: 20 }
    if (filterAppealDecision.value !== null) params.decision = filterAppealDecision.value
    const res = await listAppeals(params)
    appealList.value = res.data?.rows || []
    appealTotal.value = res.data?.total || 0
  } finally { appealLoading.value = false }
}

const fetchBlacklist = async () => {
  blacklistLoading.value = true
  try {
    const res = await listBlacklist({ page: blacklistPage.value, size: 20 })
    blacklist.value = res.data?.rows || []
    blacklistTotal.value = res.data?.total || 0
  } finally { blacklistLoading.value = false }
}

const handleForceUnban = async (row) => {
  try {
    await ElMessageBox.confirm('确定强制解封用户 #' + row.userId + '？', '确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await forceUnban(row.userId)
    ElMessage.success('已解封')
    fetchBlacklist()
  } catch (e) {}
}

const handleReversePenalty = async (row) => {
  try {
    await ElMessageBox.confirm('确定撤销惩罚记录 #' + row.id + '（用户 ' + row.userId + '）？', '确认', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await reversePenalty(row.id)
    ElMessage.success('撤销成功')
    fetchPenalties()
  } catch (e) {}
}

const openAppealDetail = (row) => {
  currentAppealData.value = { ...row }
  appealDetailVisible.value = true
}

const openUserDetail = (userId) => {
  if (!userId) return
  currentUserId.value = userId
  userDetailVisible.value = true
}

const goActivity = (id) => {
  window.open('/homepage/activity/' + id, '_blank')
}

const goReport = (id) => {
  currentReportId.value = id
  reportDetailVisible.value = true
}

const handleOpenReportFromAppeal = (reportId) => {
  currentReportId.value = reportId
  reportDetailVisible.value = true
}

onMounted(() => {
  fetchList()
  fetchStats()
})
</script>

<style scoped>
.feedback-center { height: 100%; }
.page-card { border-radius: 10px; height: 100%; display: flex; flex-direction: column; }
.page-card :deep(.el-card__body) { flex: 1; overflow: auto; }
.header-title { font-size: 16px; font-weight: bold; display: flex; align-items: center; gap: 8px; }
.stat-cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.stat-card { flex: 1; min-width: 100px; background: #f5f7fa; border-radius: 8px; padding: 12px; text-align: center; position: relative; border: 2px solid transparent; cursor: pointer; transition: all 0.2s; }
.stat-card:hover { background: #ecf5ff; }
.stat-active { border-color: #409EFF; background: #ecf5ff; }
.stat-num { font-size: 28px; font-weight: bold; line-height: 1; }
.stat-label { font-size: 12px; color: #909399; margin-top: 4px; }
.stat-tab-link { font-size: 12px; color: #409EFF; margin-top: 4px; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>
