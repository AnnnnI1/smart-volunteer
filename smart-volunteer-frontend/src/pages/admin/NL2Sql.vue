<template>
  <div>
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title"><el-icon><ChatLineRound /></el-icon> 智能数据查询（NL2SQL）</span>
      </template>

      <!-- 示例问题 -->
      <div class="examples">
        <span style="color:#909399;font-size:13px;margin-right:8px">示例：</span>
        <el-tag
          v-for="q in examples"
          :key="q"
          type="info"
          effect="plain"
          style="cursor:pointer;margin:4px"
          @click="query = q"
        >{{ q }}</el-tag>
      </div>

      <!-- 输入框 -->
      <div class="query-box">
        <el-input
          v-model="query"
          type="textarea"
          :rows="3"
          placeholder="用自然语言描述你想查询的数据，例如：查询本月所有已结束的活动"
          style="flex:1"
        />
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          @click="handleQuery"
          style="margin-left:12px;height:auto;align-self:flex-end"
        >
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </div>

      <!-- 结果区 -->
      <template v-if="result">
        <!-- 生成的 SQL -->
        <div class="sql-block">
          <div class="sql-header">
            <el-icon><Document /></el-icon>
            <span>生成的 SQL</span>
            <el-tag type="success" size="small" style="margin-left:8px">DeepSeek 生成</el-tag>
          </div>
          <pre class="sql-code">{{ result.sql }}</pre>
        </div>

        <!-- 查询结果 -->
        <div class="result-header">
          <span>查询结果</span>
          <el-tag>共 {{ result.total }} 条</el-tag>
          <el-button
            v-if="result.data && result.data.length > 0"
            size="small"
            type="success"
            plain
            @click="exportCsv"
            style="margin-left:auto"
          >
            <el-icon><Download /></el-icon>&nbsp;导出 CSV
          </el-button>
        </div>

        <el-table
          v-if="result.data && result.data.length > 0"
          :data="result.data"
          border
          stripe
          size="small"
          max-height="400"
        >
          <el-table-column
            v-for="col in columns"
            :key="col"
            :prop="col"
            :label="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
        <el-empty v-else description="查询结果为空" />
      </template>

      <!-- 错误提示 -->
      <el-alert
        v-if="errorMsg"
        :title="errorMsg"
        type="error"
        show-icon
        :closable="false"
        style="margin-top:16px"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, Search, Document, Download } from '@element-plus/icons-vue'
import { nl2sqlQuery } from '@/api/ai'

const query = ref('')
const loading = ref(false)
const result = ref(null)
const errorMsg = ref('')

const columns = computed(() => {
  if (!result.value?.data?.length) return []
  return Object.keys(result.value.data[0])
})

const examples = [
  '查询所有正在报名中的活动',
  '统计每个活动的报名人数',
  '查询有医疗技能的志愿者',
  '查询本月创建的活动',
  '统计各状态活动数量'
]

const exportCsv = () => {
  if (!result.value?.data?.length) return
  const cols = columns.value
  const rows = [cols.join(',')]
  for (const row of result.value.data) {
    rows.push(cols.map(c => {
      const v = String(row[c] ?? '')
      return v.includes(',') || v.includes('"') ? `"${v.replace(/"/g, '""')}"` : v
    }).join(','))
  }
  const blob = new Blob(['\uFEFF' + rows.join('\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `query_result_${Date.now()}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

const handleQuery = async () => {
  if (!query.value.trim()) {
    ElMessage.warning('请输入查询内容')
    return
  }
  loading.value = true
  result.value = null
  errorMsg.value = ''
  try {
    const res = await nl2sqlQuery({ query: query.value })
    result.value = res.data
  } catch (e) {
    errorMsg.value = e?.msg || '查询失败，请检查问题描述'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page-card { border-radius: 10px; }

.card-title {
  font-size: 16px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}

.examples {
  background: #f8f9fa;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.query-box {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 20px;
}

.sql-block {
  background: #1e1e1e;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.sql-header {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #ffffffa6;
  font-size: 13px;
  margin-bottom: 10px;
}

.sql-code {
  color: #4ec9b0;
  font-family: 'Consolas', monospace;
  font-size: 14px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
}
</style>
