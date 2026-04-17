<template>
  <div>
    <!-- 顶部说明卡片 -->
    <el-card class="page-card" shadow="never" style="margin-bottom:16px">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <div>
          <div style="font-size:16px;font-weight:bold;color:#303133">
            <el-icon style="color:#e6a23c"><Warning /></el-icon>
            志愿者流失预警分析
          </div>
          <div style="font-size:13px;color:#909399;margin-top:4px">
            基于取消率、活跃天数、服务时长综合评分，由 Python AI 节点实时计算
          </div>
        </div>
        <el-button type="primary" :loading="loading" @click="loadData">
          <el-icon><Refresh /></el-icon>&nbsp;重新分析
        </el-button>
      </div>
    </el-card>

    <!-- 统计概览 -->
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="6">
        <div class="stat-card" style="border-color:#f56c6c">
          <div class="stat-num red">{{ highCount }}</div>
          <div class="stat-label">高风险</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" style="border-color:#e6a23c">
          <div class="stat-num orange">{{ midCount }}</div>
          <div class="stat-label">中风险</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" style="border-color:#67c23a">
          <div class="stat-num green">{{ lowCount }}</div>
          <div class="stat-label">低风险</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card" style="border-color:#409EFF">
          <div class="stat-num blue">{{ list.length }}</div>
          <div class="stat-label">分析总人数</div>
        </div>
      </el-col>
    </el-row>

    <!-- 预警列表 -->
    <el-card shadow="never" class="page-card">
      <template #header>
        <div style="display:flex;align-items:center;gap:8px">
          <el-icon><DataAnalysis /></el-icon>
          <span style="font-weight:bold">风险详情列表</span>
          <el-tag size="small" type="info">按风险评分降序</el-tag>
        </div>
      </template>

      <el-table :data="filteredList" v-loading="loading" stripe>
        <!-- 排名 -->
        <el-table-column label="排名" width="60" align="center">
          <template #default="{ $index }">
            <span style="font-weight:bold;color:#909399">{{ $index + 1 }}</span>
          </template>
        </el-table-column>

        <!-- 志愿者 -->
        <el-table-column label="志愿者" width="110">
          <template #default="{ row }">
            <div style="font-weight:bold">{{ row.nickname || `用户${row.userId}` }}</div>
            <div style="font-size:11px;color:#c0c4cc">ID: {{ row.userId }}</div>
          </template>
        </el-table-column>

        <!-- 风险等级 -->
        <el-table-column label="风险等级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.riskColor" effect="dark" size="large">
              {{ row.riskLevel }}风险
            </el-tag>
          </template>
        </el-table-column>

        <!-- 风险评分 -->
        <el-table-column label="风险评分" width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.round(row.riskScore * 100)"
              :color="scoreColor(row.riskScore)"
              :stroke-width="10"
            />
            <span style="font-size:12px;color:#606266">{{ (row.riskScore * 100).toFixed(1) }}%</span>
          </template>
        </el-table-column>

        <!-- 行为数据 -->
        <el-table-column label="报名/取消" width="100" align="center">
          <template #default="{ row }">
            <span>{{ row.signupCount }}</span>
            <span style="color:#f56c6c"> / {{ row.cancelCount }}</span>
            <div style="font-size:11px;color:#909399">
              取消率 {{ (row.cancelRate * 100).toFixed(0) }}%
            </div>
          </template>
        </el-table-column>

        <el-table-column label="服务时长" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.totalHours < 10 ? '#f56c6c' : '#67c23a' }">
              {{ row.totalHours }}h
            </span>
          </template>
        </el-table-column>

        <el-table-column label="不活跃天数" width="100" align="center">
          <template #default="{ row }">
            <span :style="{ color: row.inactiveDays > 30 ? '#f56c6c' : '#303133' }">
              {{ row.inactiveDays === 999 ? '从未参与' : row.inactiveDays + ' 天' }}
            </span>
          </template>
        </el-table-column>

        <!-- 风险因素 -->
        <el-table-column label="风险因素" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="(f, i) in row.riskFactors"
              :key="i"
              :type="row.riskColor"
              size="small"
              effect="light"
              style="margin:2px"
            >{{ f }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 筛选栏 -->
      <div style="margin-top:14px;display:flex;align-items:center;gap:8px">
        <span style="font-size:13px;color:#606266">筛选风险等级：</span>
        <el-radio-group v-model="filterLevel" size="small">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="高">高风险</el-radio-button>
          <el-radio-button value="中">中风险</el-radio-button>
          <el-radio-button value="低">低风险</el-radio-button>
        </el-radio-group>
      </div>
    </el-card>

    <!-- 模型说明 -->
    <el-card shadow="never" class="page-card" style="margin-top:16px">
      <template #header><span style="font-weight:bold;font-size:13px">📐 模型说明</span></template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="算法">轻量级加权线性评分（模拟逻辑回归）</el-descriptions-item>
        <el-descriptions-item label="计算节点">独立 Python AI 微服务（端口 9094）</el-descriptions-item>
        <el-descriptions-item label="取消率权重">40%（高取消行为是主要流失信号）</el-descriptions-item>
        <el-descriptions-item label="不活跃天数权重">35%（60天不活跃视为完全流失）</el-descriptions-item>
        <el-descriptions-item label="低服务时长权重">15%（&lt;30小时参与不深）</el-descriptions-item>
        <el-descriptions-item label="低报名次数权重">10%（&lt;5次报名浅度用户）</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Warning, Refresh, DataAnalysis } from '@element-plus/icons-vue'
import { predictChurnRisk } from '@/api/ai'

const loading = ref(false)
const list    = ref([])
const filterLevel = ref('')

const filteredList = computed(() =>
  filterLevel.value ? list.value.filter(r => r.riskLevel === filterLevel.value) : list.value
)
const highCount  = computed(() => list.value.filter(r => r.riskLevel === '高').length)
const midCount   = computed(() => list.value.filter(r => r.riskLevel === '中').length)
const lowCount   = computed(() => list.value.filter(r => r.riskLevel === '低').length)

const scoreColor = (score) => {
  if (score >= 0.65) return '#f56c6c'
  if (score >= 0.35) return '#e6a23c'
  return '#67c23a'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await predictChurnRisk()
    list.value = res.data || []
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page-card { border-radius: 10px; }

.stat-card {
  background: #fff;
  border-radius: 10px;
  border-left: 4px solid;
  padding: 16px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.stat-num { font-size: 36px; font-weight: bold; }
.stat-num.red    { color: #f56c6c; }
.stat-num.orange { color: #e6a23c; }
.stat-num.green  { color: #67c23a; }
.stat-num.blue   { color: #409EFF; }
.stat-label { font-size: 13px; color: #606266; margin-top: 4px; }
</style>
