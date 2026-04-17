<template>
  <div>
    <el-card class="page-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title"><el-icon><DataAnalysis /></el-icon> 活动AI风控日志</span>
        </div>
      </template>

      <div style="margin-bottom:16px;display:flex;align-items:center;gap:12px">
        <el-radio-group v-model="filterPassed" @change="fetchLogs">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="1">通过</el-radio-button>
          <el-radio-button :value="0">驳回</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="fetchLogs">刷新</el-button>
      </div>

      <el-table :data="list" v-loading="loading" border stripe>
        <el-table-column label="#" type="index" width="50" />
        <el-table-column prop="activityId" label="活动ID" width="80" />
        <el-table-column prop="activityTitle" label="活动标题" min-width="150" />
        <el-table-column label="审核结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.passed === 1 ? 'success' : 'danger'" size="small">
              {{ row.passed === 1 ? '通过' : '驳回' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险标签" min-width="160">
          <template #default="{ row }">
            <template v-if="row.riskTags && row.riskTags.trim()">
              <el-tag
                v-for="tag in row.riskTags.split(',')"
                :key="tag"
                size="small"
                type="warning"
                effect="plain"
                style="margin:2px"
              >{{ tag }}</el-tag>
            </template>
            <span v-else style="color:#909399">—</span>
          </template>
        </el-table-column>
        <el-table-column label="AI建议" min-width="180">
          <template #default="{ row }">
            <span style="font-size:12px;color:#606266">{{ getSuggestion(row.auditResult) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核时间" width="160">
          <template #default="{ row }">{{ fmt(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchLogs"
        style="margin-top:16px;justify-content:flex-end"
      />
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="AI风控审核详情" width="620px" destroy-on-close>
      <div v-if="currentRow">
        <!-- 基础信息 -->
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="活动ID">{{ currentRow.activityId }}</el-descriptions-item>
          <el-descriptions-item label="审核结果">
            <el-tag :type="currentRow.passed === 1 ? 'success' : 'danger'" effect="dark">
              {{ currentRow.passed === 1 ? '✓ 通过' : '✗ 驳回' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="活动标题" :span="2">{{ currentRow.activityTitle }}</el-descriptions-item>
          <el-descriptions-item label="审核时间" :span="2">{{ fmt(currentRow.createTime) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 解析后的审核内容 -->
        <template v-if="getParsedData(currentRow.auditResult)">
          <!-- 合规判断 + 建议 -->
          <div class="audit-section-title">审核结论</div>
          <el-alert
            :title="getParsedData(currentRow.auditResult).is_compliant ? '内容合规，可发布' : '内容不合规，建议修改后重新提交'"
            :type="getParsedData(currentRow.auditResult).is_compliant ? 'success' : 'error'"
            :closable="false"
            show-icon
            style="margin-bottom:12px"
          />
          <div v-if="getParsedData(currentRow.auditResult).suggestion" style="background:#f5f7fa;border-radius:6px;padding:10px 14px;margin-bottom:16px;font-size:13px;color:#303133;line-height:1.7">
            <b style="color:#606266">AI建议：</b>{{ getParsedData(currentRow.auditResult).suggestion }}
          </div>

          <!-- 风险原因 -->
          <template v-if="getParsedData(currentRow.auditResult).risk_reasons?.length">
            <div class="audit-section-title">风险原因</div>
            <div style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:16px">
              <el-tag
                v-for="r in getParsedData(currentRow.auditResult).risk_reasons"
                :key="r"
                type="danger"
                effect="light"
              >{{ r }}</el-tag>
            </div>
          </template>

          <!-- 内容审核细节 -->
          <template v-if="getParsedData(currentRow.auditResult).content_review">
            <div class="audit-section-title">内容审核细节</div>
            <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
              <el-descriptions-item label="标题安全">
                <el-tag :type="getParsedData(currentRow.auditResult).content_review.title_safe ? 'success' : 'danger'" size="small">
                  {{ getParsedData(currentRow.auditResult).content_review.title_safe ? '✓ 安全' : '✗ 有风险' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="描述安全">
                <el-tag :type="getParsedData(currentRow.auditResult).content_review.description_safe ? 'success' : 'danger'" size="small">
                  {{ getParsedData(currentRow.auditResult).content_review.description_safe ? '✓ 安全' : '✗ 有风险' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="getParsedData(currentRow.auditResult).content_review.sensitive_words?.length" label="敏感词" :span="2">
                <el-tag
                  v-for="w in getParsedData(currentRow.auditResult).content_review.sensitive_words"
                  :key="w"
                  type="warning"
                  size="small"
                  style="margin:2px"
                >{{ w }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-else label="敏感词" :span="2">
                <span style="color:#67c23a">无</span>
              </el-descriptions-item>
            </el-descriptions>
          </template>

          <!-- 风险等级 -->
          <div v-if="getParsedData(currentRow.auditResult).risk_level" style="margin-bottom:12px">
            <b style="color:#606266;font-size:13px">综合风险等级：</b>
            <el-tag
              :type="getParsedData(currentRow.auditResult).risk_level === '高' ? 'danger' : getParsedData(currentRow.auditResult).risk_level === '中' ? 'warning' : 'success'"
              effect="dark"
              style="margin-left:8px"
            >{{ getParsedData(currentRow.auditResult).risk_level }}</el-tag>
          </div>
        </template>
        <template v-else>
          <div class="audit-section-title">原始报告</div>
          <pre style="background:#f5f7fa;padding:12px;border-radius:6px;font-size:12px;max-height:280px;overflow:auto;color:#303133;white-space:pre-wrap;word-break:break-all">{{ formatAuditReport(currentRow.auditResult) }}</pre>
        </template>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { DataAnalysis } from '@element-plus/icons-vue'
import { getActivityAuditLogs } from '@/api/activity'

const loading = ref(false)
const list = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterPassed = ref(null)
const detailVisible = ref(false)
const currentRow = ref(null)

const fmt = (d) => d ? String(d).replace('T', ' ').substring(0, 19) : '-'

/** 解析 auditResult JSON，取 data 字段 */
const getParsedData = (raw) => {
  try {
    if (!raw || raw === '{}') return null
    const obj = JSON.parse(raw)
    return obj.data || null
  } catch {
    return null
  }
}

/** 从审核结果中提取建议（列表中简要展示） */
const getSuggestion = (raw) => {
  const d = getParsedData(raw)
  if (!d) return '—'
  return d.suggestion || (d.is_compliant ? '内容合规' : '内容不合规')
}

const formatAuditReport = (raw) => {
  if (!raw || raw === '{}') return '暂无报告数据'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw || '暂无报告数据'
  }
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filterPassed.value !== null) params.passed = filterPassed.value
    const res = await getActivityAuditLogs(params)
    list.value = res.data?.rows || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const showDetail = (row) => {
  currentRow.value = row
  detailVisible.value = true
}

onMounted(fetchLogs)
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
.audit-section-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #409EFF;
}
</style>
