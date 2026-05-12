<template>
  <el-dialog v-model="visible" title="举报详情" width="680px" destroy-on-close @close="reset">
    <div v-if="detailLoading" v-loading="true" style="height:200px" />

    <div v-else-if="report">
      <!-- 基本信息 -->
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="举报ID">{{ report.id }}</el-descriptions-item>
        <el-descriptions-item label="举报类型">
          <el-tag :type="report.reportType === 'AR' ? 'danger' : 'warning'" size="small">
            {{ report.reportType === 'AR' ? '活动举报' : '志愿者违规' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分类编号">{{ report.categoryCode }}</el-descriptions-item>
        <el-descriptions-item label="举报时间">{{ fmt(report.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="被举报人">
          <el-button link type="primary" @click="openUserDetail(report.reportedUserId)">
            {{ report.reportedNickname || report.reportedUsername || '-' }}
          </el-button>
          <span style="color:#909399;font-size:11px;margin-left:6px">ID: {{ report.reportedUserId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="被举报人角色">
          {{ roleName(report.reportedRole) }}
        </el-descriptions-item>
        <el-descriptions-item label="被举报人信用分">
          <span :style="{ color: report.reportedCreditScore < 40 ? '#f56c6c' : '#67c23a', fontWeight: 'bold' }">
            {{ report.reportedCreditScore ?? '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="举报人">
          <el-button link type="primary" @click="openUserDetail(report.reporterId)">
            {{ report.reporterNickname || report.reporterUsername || '-' }}
          </el-button>
          <span v-if="report.reporterId" style="color:#909399;font-size:11px;margin-left:6px">ID: {{ report.reporterId }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="关联活动" :span="2">
          <span v-if="report.activityId">#{{ report.activityId }} - {{ report.activityTitle || '' }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 举报说明 -->
      <div class="section-title">举报说明</div>
      <el-card shadow="never" class="content-card" style="margin-bottom:16px">
        <p style="line-height:1.8;margin:0">{{ report.description || '无' }}</p>
      </el-card>

      <!-- AI 分析报告 -->
      <div v-if="report.aiAnalysis" class="section-title">
        AI 分析报告
        <el-tag type="success" size="small" style="margin-left:8px">
          置信度 {{ Math.round((report.aiAnalysis.confidence || 0) * 100) }}%
        </el-tag>
      </div>
      <el-card v-if="report.aiAnalysis" shadow="never" class="ai-card" style="margin-bottom:16px">
        <div class="ai-section">
          <div class="ai-row">
            <span class="ai-label">风险等级：</span>
            <el-tag :type="riskTagType(report.aiAnalysis.risk_level)" effect="dark">
              {{ riskLevelName(report.aiAnalysis.risk_level) }}
            </el-tag>
          </div>
          <div class="ai-row">
            <span class="ai-label">违规类型：</span>
            <el-tag v-for="v in (report.aiAnalysis.violation_types || [])" :key="v" type="danger" size="small" style="margin-right:4px">
              {{ v }}
            </el-tag>
          </div>
          <div class="ai-row">
            <span class="ai-label">证据强度：</span>
            <el-progress :percentage="Math.round((report.aiAnalysis.evidence_strength || 0) * 100)"
              :color="evidenceColor(report.aiAnalysis.evidence_strength)"
              style="width:200px" :stroke-width="6" />
          </div>
        </div>

        <el-divider v-if="report.aiAnalysis.mitigating_factors?.length || report.aiAnalysis.aggravating_factors?.length" />
        <div class="ai-section" v-if="report.aiAnalysis.mitigating_factors?.length">
          <div class="ai-label" style="color:#67c23a">支持因素：</div>
          <ul style="margin:4px 0 0 16px;padding:0">
            <li v-for="(f, i) in report.aiAnalysis.mitigating_factors" :key="i" style="font-size:13px;color:#606266">{{ f }}</li>
          </ul>
        </div>
        <div class="ai-section" v-if="report.aiAnalysis.aggravating_factors?.length">
          <div class="ai-label" style="color:#f56c6c">不利因素：</div>
          <ul style="margin:4px 0 0 16px;padding:0">
            <li v-for="(f, i) in report.aiAnalysis.aggravating_factors" :key="i" style="font-size:13px;color:#606266">{{ f }}</li>
          </ul>
        </div>

        <el-divider v-if="report.aiAnalysis.suggested_penalty" />
        <div class="ai-section" v-if="report.aiAnalysis.suggested_penalty">
          <div class="ai-label">AI 建议惩罚</div>
          <div class="penalty-suggestions">
            <div v-if="report.aiAnalysis.suggested_penalty.credit_deduct" class="penalty-item">
              <span>积分扣除：</span>
              <b style="color:#f56c6c">-{{ report.aiAnalysis.suggested_penalty.credit_deduct }}</b>
            </div>
            <div v-if="report.aiAnalysis.suggested_penalty.ban_days" class="penalty-item">
              <span>封禁时长：</span>
              <b style="color:#f56c6c">{{ report.aiAnalysis.suggested_penalty.ban_days }} 天</b>
            </div>
            <div v-if="report.aiAnalysis.suggested_penalty.activity_limit !== 'none'" class="penalty-item">
              <span>限制发起活动：</span>
              <b>{{ report.aiAnalysis.suggested_penalty.activity_limit === 'temporary' ? '临时' : report.aiAnalysis.suggested_penalty.activity_limit }}</b>
            </div>
            <div v-if="report.aiAnalysis.suggested_penalty.demotion" class="penalty-item">
              <span>组织者降级：</span>
              <b style="color:#e6a23c">是</b>
            </div>
          </div>
        </div>

        <div v-if="report.aiAnalysis.reasoning" style="margin-top:8px;font-size:12px;color:#909399">
          分析依据：{{ report.aiAnalysis.reasoning }}
        </div>
      </el-card>
      <div v-else-if="report.status !== 2 && report.status !== 3" class="no-ai-hint">
        <el-icon><InfoFilled /></el-icon>
        暂无AI分析结果，可点击下方按钮进行AI分析
      </div>

      <!-- 处理操作 -->
      <template v-if="report.status === 1">
        <div class="section-title">处理决定</div>
        <el-form :model="processForm" label-width="100px" style="margin-bottom:16px">
          <el-form-item label="处理方式">
            <el-radio-group v-model="processForm.decision">
              <el-radio :value="0">采纳AI建议</el-radio>
              <el-radio :value="1">调整惩罚参数</el-radio>
              <el-radio :value="2">否决（无效举报）</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 调整惩罚参数 -->
          <template v-if="processForm.decision === 1">
            <el-form-item label="惩罚类型">
              <el-checkbox-group v-model="processForm.penaltyTypes">
                <el-checkbox label="credit_deduct">积分扣除</el-checkbox>
                <el-checkbox label="ban">账号封禁</el-checkbox>
                <el-checkbox label="activity_limit">限制发起活动</el-checkbox>
                <el-checkbox label="demotion">组织者降级</el-checkbox>
              </el-checkbox-group>
            </el-form-item>
            <el-form-item v-if="processForm.penaltyTypes.includes('credit_deduct')" label="积分扣除">
              <el-input-number v-model="processForm.creditDeduct" :min="0" :max="500" />
            </el-form-item>
            <el-form-item v-if="processForm.penaltyTypes.includes('ban')" label="封禁天数">
              <el-input-number v-model="processForm.banDays" :min="1" :max="365" />
            </el-form-item>
          </template>

          <!-- 否决理由 -->
          <el-form-item v-if="processForm.decision === 2" label="否决理由">
            <el-input v-model="processForm.adminDecision" type="textarea" :rows="3" placeholder="请填写否决理由" />
          </el-form-item>
          <el-form-item v-if="processForm.decision !== 2" label="补充说明">
            <el-input v-model="processForm.adminDecision" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
        </el-form>
      </template>

      <!-- 已处理结果 -->
      <template v-if="report.status === 2 || report.status === 3">
        <div class="section-title">处理结果</div>
        <el-card shadow="never" style="margin-bottom:16px;background:#f5f7fa">
          <div style="font-size:13px;color:#606266">
            <b>决定：</b>{{ report.status === 2 ? '已结案' : '无效举报' }}<br/>
            <b>说明：</b>{{ report.adminDecision || '-' }}
          </div>
        </el-card>
      </template>

      <!-- 申诉记录 -->
      <template v-if="report.appeals?.length">
        <div class="section-title">申诉记录</div>
        <el-card shadow="never" v-for="(appeal, i) in report.appeals" :key="i" style="margin-bottom:8px">
          <div style="font-size:13px">
            <b>申诉理由：</b>{{ appeal.appealReason }}<br/>
            <b>结果：</b>
            <el-tag :type="appeal.decision === 1 ? 'success' : appeal.decision === 2 ? 'danger' : 'info'" size="small">
              {{ appeal.decisionDesc }}
            </el-tag>
            <br/>
            <template v-if="appeal.adminResponse">
              <b>管理员回复：</b>{{ appeal.adminResponse }}
            </template>
          </div>
        </el-card>
      </template>
    </div>

    <template #footer>
      <div style="display:flex;justify-content:space-between;align-items:center;width:100%">
        <el-button v-if="report && !report.aiAnalysis && report.status !== 2 && report.status !== 3"
          type="primary" plain @click="handleAnalyze" :loading="analyzing">
          AI分析
        </el-button>
        <div v-else />
        <div style="display:flex;gap:8px">
          <el-button @click="visible = false">关闭</el-button>
          <el-button v-if="report && report.status === 1" type="primary" @click="handleProcess" :loading="processing">
            确认处理
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>

  <UserDetailDialog v-model="userDetailVisible" :user-id="currentUserId" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { getReportDetail, processReport, batchAnalyzeReports } from '@/api/report'
import UserDetailDialog from './UserDetailDialog.vue'

const props = defineProps({ modelValue: Boolean, reportId: { type: Number, default: null } })
const emit = defineEmits(['update:modelValue', 'refresh'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const detailLoading = ref(false)
const report = ref(null)
const analyzing = ref(false)
const processing = ref(false)
const userDetailVisible = ref(false)
const currentUserId = ref(null)

const processForm = ref({
  decision: 0,
  penaltyTypes: ['credit_deduct'],
  creditDeduct: 100,
  banDays: 30,
  adminDecision: ''
})

watch(() => props.reportId, async (id) => {
  if (id && visible.value) {
    await fetchDetail(id)
  }
}, { immediate: true })

watch(visible, async (v) => {
  if (v && props.reportId) {
    await fetchDetail(props.reportId)
  }
})

const fetchDetail = async (id) => {
  detailLoading.value = true
  try {
    const res = await getReportDetail(id)
    report.value = res.data
  } finally {
    detailLoading.value = false
  }
}

const handleAnalyze = async () => {
  analyzing.value = true
  try {
    await batchAnalyzeReports([props.reportId])
    ElMessage.success('AI分析完成')
    await fetchDetail(props.reportId)
  } catch (e) {
    ElMessage.error('分析失败')
  } finally {
    analyzing.value = false
  }
}

const handleProcess = async () => {
  processing.value = true
  try {
    const data = {
      decision: processForm.value.decision,
      adminDecision: processForm.value.adminDecision,
      penaltyTypes: processForm.value.penaltyTypes,
      creditDeduct: processForm.value.creditDeduct,
      banDays: processForm.value.banDays,
      activityLimit: processForm.value.penaltyTypes.includes('activity_limit'),
      demotion: processForm.value.penaltyTypes.includes('demotion')
    }
    await processReport(props.reportId, data)
    ElMessage.success('处理完成')
    visible.value = false
    emit('refresh')
  } catch (e) {
    ElMessage.error('处理失败')
  } finally {
    processing.value = false
  }
}

const reset = () => {
  report.value = null
  processForm.value = {
    decision: 0,
    penaltyTypes: ['credit_deduct'],
    creditDeduct: 100,
    banDays: 30,
    adminDecision: ''
  }
}

const openUserDetail = (userId) => {
  if (!userId) return
  currentUserId.value = userId
  userDetailVisible.value = true
}

const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').substring(0, 16) : '-'
const roleName = (r) => ({ 0: '管理员', 1: '志愿者', 2: '组织者' }[r] || '-')
const riskLevelName = (l) => ({ high: '高', medium: '中', low: '低' }[l] || l || '-')
const riskTagType = (l) => ({ high: 'danger', medium: 'warning', low: 'success' }[l] || 'info')
const evidenceColor = (v) => v > 0.7 ? '#f56c6c' : v > 0.4 ? '#e6a23c' : '#67c23a'
</script>

<style scoped>
.section-title { font-size: 13px; font-weight: bold; color: #606266; margin-bottom: 8px; padding-left: 8px; border-left: 3px solid #409EFF; }
.content-card { background: #fafafa; border-radius: 8px; }
.ai-card { background: linear-gradient(135deg, #f0f7ff 0%, #fafafa 100%); border: 1px solid #d0e8ff; }
.ai-section { margin-bottom: 8px; }
.ai-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ai-label { font-size: 13px; font-weight: bold; color: #303133; min-width: 80px; }
.penalty-suggestions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 4px; }
.penalty-item { background: #fff; border: 1px solid #d0e8ff; border-radius: 4px; padding: 4px 10px; font-size: 13px; }
.no-ai-hint { text-align: center; padding: 16px; color: #909399; font-size: 13px; background: #f5f7fa; border-radius: 8px; margin-bottom: 16px; }
</style>
