<template>
  <div class="admin-ai">
    <div style="margin-bottom:16px">
      <span style="font-size:18px;font-weight:bold">AI 运营中心</span>
      <el-tag type="primary" size="small" style="margin-left:8px">智能决策</el-tag>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="main-tabs">

      <!-- ============================================================ -->
      <!-- Tab 1: 运营概览 -->
      <!-- ============================================================ -->
      <el-tab-pane label="运营概览" name="overview">
        <div v-loading="overviewLoading">
          <!-- 4 张统计卡片 -->
          <el-row :gutter="16" style="margin-bottom:20px">
            <el-col :span="6">
              <div class="stat-card" style="border-color:#f56c6c">
                <div class="stat-num red">{{ highCount }}</div>
                <div class="stat-label">高风险志愿者</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card" style="border-color:#e6a23c">
                <div class="stat-num orange">{{ midCount }}</div>
                <div class="stat-label">中风险志愿者</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card" style="border-color:#409EFF">
                <div class="stat-num blue">{{ highCount + midCount }}</div>
                <div class="stat-label">待跟进人数</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="stat-card" style="border-color:#67c23a">
                <div class="stat-num green">{{ healthPercent }}%</div>
                <div class="stat-label">平台健康度</div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="16">
            <!-- 左：风险分布 -->
            <el-col :span="12">
              <el-card shadow="never" class="section-card">
                <template #header>
                  <span class="card-title"><el-icon><Warning /></el-icon> 风险等级分布</span>
                </template>
                <div class="risk-bar-item">
                  <span class="risk-label red-text">高风险</span>
                  <el-progress
                    :percentage="riskPercent('高')"
                    :stroke-width="14"
                    color="#f56c6c"
                    style="flex:1"
                  />
                  <span class="risk-count">{{ highCount }} 人</span>
                </div>
                <div class="risk-bar-item">
                  <span class="risk-label orange-text">中风险</span>
                  <el-progress
                    :percentage="riskPercent('中')"
                    :stroke-width="14"
                    color="#e6a23c"
                    style="flex:1"
                  />
                  <span class="risk-count">{{ midCount }} 人</span>
                </div>
                <div class="risk-bar-item">
                  <span class="risk-label green-text">低风险</span>
                  <el-progress
                    :percentage="riskPercent('低')"
                    :stroke-width="14"
                    color="#67c23a"
                    style="flex:1"
                  />
                  <span class="risk-count">{{ lowCount }} 人</span>
                </div>
              </el-card>
            </el-col>

            <!-- 右：活动状态分布 -->
            <el-col :span="12">
              <el-card shadow="never" class="section-card">
                <template #header>
                  <span class="card-title"><el-icon><Calendar /></el-icon> 活动状态分布</span>
                </template>
                <el-row :gutter="12">
                  <el-col :span="12" v-for="s in activityStats" :key="s.label">
                    <div class="act-stat-item" :style="{ borderColor: s.color }">
                      <div class="act-stat-num" :style="{ color: s.color }">{{ s.count }}</div>
                      <div class="act-stat-label">{{ s.label }}</div>
                    </div>
                  </el-col>
                </el-row>
              </el-card>
            </el-col>
          </el-row>

          <!-- 快捷操作 -->
          <div style="margin-top:20px;display:flex;gap:12px">
            <el-button type="danger" @click="activeTab='risk'">
              <el-icon><Warning /></el-icon>&nbsp;查看流失预警
            </el-button>
            <el-button type="primary" @click="activeTab='diagnose'">
              <el-icon><DataAnalysis /></el-icon>&nbsp;开始活动诊断
            </el-button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <!-- Tab 2: 流失预警（增强版）-->
      <!-- ============================================================ -->
      <el-tab-pane label="流失预警" name="risk">
        <el-card shadow="never" class="section-card">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span class="card-title"><el-icon><Warning /></el-icon> 志愿者流失预警列表</span>
              <el-button type="primary" size="small" :loading="riskLoading" @click="loadRisk">
                <el-icon><Refresh /></el-icon>&nbsp;重新分析
              </el-button>
            </div>
          </template>

          <!-- 筛选 -->
          <div style="margin-bottom:12px;display:flex;align-items:center;gap:8px">
            <span style="font-size:13px;color:#606266">筛选风险等级：</span>
            <el-radio-group v-model="filterLevel" size="small">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="高">高风险</el-radio-button>
              <el-radio-button value="中">中风险</el-radio-button>
              <el-radio-button value="低">低风险</el-radio-button>
            </el-radio-group>
          </div>

          <el-table :data="filteredList" v-loading="riskLoading" stripe>
            <el-table-column label="排名" width="60" align="center">
              <template #default="{ $index }">
                <span style="font-weight:bold;color:#909399">{{ $index + 1 }}</span>
              </template>
            </el-table-column>

            <el-table-column label="志愿者" width="120">
              <template #default="{ row }">
                <div style="font-weight:bold">{{ row.nickname || `用户${row.userId}` }}</div>
                <div style="font-size:11px;color:#c0c4cc">ID: {{ row.userId }}</div>
              </template>
            </el-table-column>

            <el-table-column label="风险等级" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.riskColor" effect="dark">{{ row.riskLevel }}风险</el-tag>
              </template>
            </el-table-column>

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

            <el-table-column label="报名/取消" width="100" align="center">
              <template #default="{ row }">
                <span>{{ row.signupCount }}</span>
                <span style="color:#f56c6c"> / {{ row.cancelCount }}</span>
                <div style="font-size:11px;color:#909399">取消率 {{ (row.cancelRate * 100).toFixed(0) }}%</div>
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

            <el-table-column label="风险因素" min-width="180">
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

            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  plain
                  @click="openRecommendDialog(row)"
                >推荐活动</el-button>
                <el-button
                  type="warning"
                  size="small"
                  plain
                  style="margin-left:4px"
                  @click="openCreditBoostDialog(row)"
                >积分激励</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ============================================================ -->
      <!-- Tab 3: 活动诊断 -->
      <!-- ============================================================ -->
      <el-tab-pane label="活动诊断" name="diagnose">
        <!-- Step A: 选择活动 -->
        <el-card shadow="never" class="section-card" style="margin-bottom:16px">
          <template #header>
            <span class="card-title"><el-icon><Search /></el-icon> 选择诊断活动</span>
          </template>
          <div style="display:flex;align-items:center;gap:12px;flex-wrap:wrap">
            <el-select
              v-model="selectedActivityId"
              placeholder="请选择活动"
              filterable
              style="width:340px"
            >
              <el-option
                v-for="a in activityList"
                :key="a.id"
                :value="a.id"
                :label="a.title"
              >
                <span>{{ a.title }}</span>
                <el-tag :type="statusTagType(a.status)" size="small" style="margin-left:8px">
                  {{ statusLabel(a.status) }}
                </el-tag>
              </el-option>
            </el-select>
            <el-button
              type="primary"
              :disabled="!selectedActivityId"
              :loading="diagnoseLoading"
              @click="runDiagnose"
            >
              <el-icon><DataAnalysis /></el-icon>&nbsp;开始诊断
            </el-button>
          </div>
        </el-card>

        <!-- Step B: 诊断结果 -->
        <div v-if="diagnoseResult">
          <!-- AI 精推横幅（双阶段 Transformer + DeepSeek，有结果才显示） -->
          <el-card
            v-if="diagnoseResult.hybridResult?.candidates?.length"
            shadow="never"
            class="section-card ai-banner-card"
            style="margin-bottom:16px;border-left:4px solid #7c3aed"
          >
            <template #header>
              <span class="card-title" style="color:#7c3aed">
                <el-icon><Connection /></el-icon>
                AI 精推候选人
                <el-tag size="small" effect="dark" color="#7c3aed" style="border-color:#7c3aed;margin-left:6px">
                  {{ diagnoseResult.hybridResult.stage === 'hybrid' ? '双阶段·Transformer+DeepSeek' : diagnoseResult.hybridResult.stage }}
                </el-tag>
                <span v-if="diagnoseResult.hybridResult.modelInfo" style="font-size:12px;color:#c0c4cc;margin-left:8px;font-weight:normal">
                  {{ diagnoseResult.hybridResult.modelInfo }}
                </span>
              </span>
            </template>

            <!-- AI 推荐理由（有 finalRecommendation 时展示） -->
            <div
              v-if="diagnoseResult.hybridResult.finalRecommendation?.aiReasoning"
              style="background:#faf5ff;border:1px solid #e9d5ff;border-radius:6px;padding:10px 14px;margin-bottom:14px;display:flex;align-items:flex-start;gap:8px"
            >
              <span style="font-size:18px;flex-shrink:0">🤖</span>
              <div>
                <span style="font-size:12px;color:#7c3aed;font-weight:bold;margin-right:6px">DeepSeek 推荐理由</span>
                <span style="font-size:13px;color:#303133;line-height:1.7">
                  {{ diagnoseResult.hybridResult.finalRecommendation.aiReasoning }}
                </span>
              </div>
            </div>

            <!-- 候选人卡片列表 -->
            <el-row :gutter="12">
              <el-col
                v-for="(c, idx) in diagnoseResult.hybridResult.candidates.slice(0, 5)"
                :key="c.user_id || c.userId"
                :span="Math.floor(24 / Math.min(diagnoseResult.hybridResult.candidates.length, 5))"
              >
                <div
                  class="hybrid-candidate"
                  :class="{
                    'hybrid-top': diagnoseResult.hybridResult.finalRecommendation?.recommendedUserId === (c.user_id || c.userId)
                  }"
                >
                  <!-- 排名 + 最佳标记 -->
                  <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:6px">
                    <div class="hybrid-rank">#{{ c.rank || idx + 1 }}</div>
                    <el-tag
                      v-if="diagnoseResult.hybridResult.finalRecommendation?.recommendedUserId === (c.user_id || c.userId)"
                      size="small" effect="dark" color="#7c3aed" style="border-color:#7c3aed"
                    >AI首推</el-tag>
                  </div>
                  <!-- 姓名 -->
                  <div class="hybrid-name">{{ c.real_name || c.realName || c.nickname || `用户${c.user_id || c.userId}` }}</div>
                  <!-- 技能 -->
                  <div style="margin:4px 0;min-height:20px">
                    <el-tag
                      v-for="s in (c.skills || '').split(',').filter(Boolean).slice(0,3)"
                      :key="s"
                      size="small"
                      type="info"
                      style="margin:1px;font-size:11px"
                    >{{ s }}</el-tag>
                  </div>
                  <!-- 相似度进度条 -->
                  <el-progress
                    :percentage="Math.min(100, Math.round((c.similarity_score || c.similarityScore || c.finalScore || 0) * 100))"
                    :stroke-width="6"
                    :color="diagnoseResult.hybridResult.finalRecommendation?.recommendedUserId === (c.user_id || c.userId) ? '#7c3aed' : '#409EFF'"
                  />
                  <div style="font-size:11px;color:#909399;text-align:center;margin:3px 0 8px">
                    匹配度 {{ Math.min(100, ((c.similarity_score || c.similarityScore || c.finalScore || 0) * 100)).toFixed(1) }}%
                  </div>
                  <!-- 邀请按钮 -->
                  <div style="text-align:center">
                    <el-tag
                      v-if="diagnoseRegisteredUserIds.has(c.user_id || c.userId)"
                      type="success" size="small"
                    >已报名 ✓</el-tag>
                    <el-button
                      v-else
                      type="primary"
                      size="small"
                      plain
                      style="width:100%"
                      :loading="diagnoseInvitingId === (c.user_id || c.userId)"
                      @click="inviteFromDiagnose(c.user_id || c.userId, selectedActivityId)"
                    >邀请报名</el-button>
                  </div>
                </div>
              </el-col>
            </el-row>
          </el-card>

          <el-row :gutter="16">
            <!-- 卡片1: 基本信息 -->
            <el-col :span="12">
              <el-card shadow="never" class="section-card diagnose-card">
                <template #header>
                  <span class="card-title"><el-icon><InfoFilled /></el-icon> 活动基本信息</span>
                </template>
                <el-descriptions :column="1" border size="small">
                  <el-descriptions-item label="标题">
                    <strong>{{ diagnoseResult.activity.title }}</strong>
                  </el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag :type="statusTagType(diagnoseResult.activity.status)">
                      {{ statusLabel(diagnoseResult.activity.status) }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="总名额">{{ diagnoseResult.activity.capacity }}</el-descriptions-item>
                  <el-descriptions-item label="已报名">{{ diagnoseResult.activity.registeredCount }}</el-descriptions-item>
                  <el-descriptions-item label="剩余名额">
                    <span :style="{ color: diagnoseResult.activity.remainingSlots <= 5 ? '#f56c6c' : '#67c23a' }">
                      {{ diagnoseResult.activity.remainingSlots }}
                    </span>
                  </el-descriptions-item>
                </el-descriptions>
                <div style="margin-top:12px">
                  <div style="font-size:12px;color:#909399;margin-bottom:6px">所需技能</div>
                  <template v-if="diagnoseResult.activity.requiredSkills?.length">
                    <el-tag
                      v-for="s in diagnoseResult.activity.requiredSkills"
                      :key="s"
                      type="primary"
                      size="small"
                      style="margin:2px"
                    >{{ s }}</el-tag>
                  </template>
                  <span v-else style="color:#c0c4cc;font-size:13px">未设置技能要求</span>
                </div>
              </el-card>
            </el-col>

            <!-- 卡片2: 报名转化分析 -->
            <el-col :span="12">
              <el-card shadow="never" class="section-card diagnose-card">
                <template #header>
                  <span class="card-title"><el-icon><TrendCharts /></el-icon> 报名转化分析</span>
                </template>
                <div v-if="!diagnoseResult.conversion">
                  <span class="empty-hint">暂无报名数据</span>
                </div>
                <div v-else>
                  <el-row :gutter="8" style="margin-bottom:16px">
                    <el-col :span="12">
                      <el-statistic title="已报名" :value="diagnoseResult.conversion.signed" />
                    </el-col>
                    <el-col :span="12">
                      <el-statistic title="已签到" :value="diagnoseResult.conversion.checkedIn" />
                    </el-col>
                    <el-col :span="12" style="margin-top:12px">
                      <el-statistic title="已取消" :value="diagnoseResult.conversion.cancelled" />
                    </el-col>
                    <el-col :span="12" style="margin-top:12px">
                      <el-statistic title="缺席" :value="diagnoseResult.conversion.absent" />
                    </el-col>
                  </el-row>

                  <div style="margin-bottom:8px">
                    <span style="font-size:13px;color:#606266">到场率</span>
                    <el-progress
                      :percentage="diagnoseResult.conversion.attendanceRate"
                      :color="attendanceColor(diagnoseResult.conversion.attendanceRate)"
                      :stroke-width="12"
                    />
                  </div>
                  <div>
                    <span style="font-size:13px;color:#606266">取消率</span>
                    <el-progress
                      :percentage="diagnoseResult.conversion.cancelRate"
                      color="#f56c6c"
                      :stroke-width="12"
                    />
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>

      <!-- ============================================================ -->
      <!-- Tab 4: 积分管理 -->
      <!-- ============================================================ -->
      <el-tab-pane label="积分管理" name="credit">
        <el-card shadow="never" class="section-card">
          <template #header>
            <span class="card-title"><el-icon><Trophy /></el-icon> 积分手动调整</span>
          </template>
          <el-form :model="creditForm" label-width="90px" style="max-width:480px">
            <el-form-item label="目标用户">
              <el-select
                v-model="creditForm.userId"
                filterable
                placeholder="在流失预警列表中选择，或直接输入用户ID"
                style="width:100%"
              >
                <el-option
                  v-for="r in riskList"
                  :key="r.userId"
                  :value="r.userId"
                  :label="`${r.nickname || '用户' + r.userId}（${r.riskLevel}风险）`"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="调整积分">
              <el-input-number
                v-model="creditForm.delta"
                :min="-1000"
                :max="1000"
                :step="10"
                style="width:200px"
              />
              <span style="font-size:12px;color:#909399;margin-left:8px">正数=加分，负数=扣分</span>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="creditForm.remark" placeholder="如：流失预警人工激励" style="width:100%" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="creditAdjusting" @click="doAdjustCredit">
                确认调整
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 积分调整历史说明 -->
        <el-card shadow="never" class="section-card" style="margin-top:16px">
          <template #header><span class="card-title">积分规则说明</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="签到奖励">+10 分（activity-checkin 事件）</el-descriptions-item>
            <el-descriptions-item label="完成奖励">+50 分（活动结束，仅已签到者）</el-descriptions-item>
            <el-descriptions-item label="铜牌门槛">50 分 🥉</el-descriptions-item>
            <el-descriptions-item label="银牌门槛">200 分 🥈</el-descriptions-item>
            <el-descriptions-item label="金牌门槛">500 分 🥇</el-descriptions-item>
            <el-descriptions-item label="管理员调整">本页手动 +/- 任意分值</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- ====================================================== -->
    <!-- 推荐活动弹窗 -->
    <!-- ====================================================== -->
    <el-dialog
      v-model="recommendDialog"
      :title="`为「${currentRiskUser?.nickname || '用户' + currentRiskUser?.userId}」推荐活动`"
      width="640px"
      destroy-on-close
    >
      <div v-if="recommendLoading" v-loading="true" style="height:120px" />
      <div v-else-if="!recommendList.length" class="empty-hint" style="padding:20px">
        暂无推荐活动（该志愿者可能未设置技能标签）
      </div>
      <div v-else>
        <div v-if="recommendList.length" style="font-size:13px;color:#909399;margin-bottom:12px">
          推荐基于该志愿者的技能档案和历史参与记录
        </div>
        <div
          v-for="item in recommendList"
          :key="item.activityId"
          class="recommend-card"
        >
          <div class="recommend-card-left">
            <div class="recommend-title">{{ item.title }}</div>
            <div style="margin-top:4px">
              <el-tag
                v-for="s in (item.matchedSkills || [])"
                :key="s"
                size="small"
                type="success"
                style="margin:2px"
              >{{ s }}</el-tag>
            </div>
            <div style="font-size:12px;color:#909399;margin-top:4px">
              剩余名额：
              <span :style="{ color: item.remainQuota <= 5 ? '#f56c6c' : '#67c23a' }">
                {{ item.remainQuota }}
              </span>
              &nbsp;·&nbsp;匹配度
              <el-progress
                :percentage="Math.round((item.similarity || 0) * 100)"
                :stroke-width="6"
                color="#409EFF"
                style="display:inline-flex;width:80px;vertical-align:middle;margin-left:4px"
              />
            </div>
          </div>
          <el-button
            type="primary"
            size="small"
            :loading="registeringId === item.activityId"
            @click="doAdminRegister(item.activityId)"
          >邀请报名</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="recommendDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ====================================================== -->
    <!-- 积分激励快捷弹窗（流失预警行内） -->
    <!-- ====================================================== -->
    <el-dialog
      v-model="creditBoostDialog"
      :title="`积分激励 — ${creditBoostUser?.nickname || '用户' + creditBoostUser?.userId}`"
      width="400px"
      destroy-on-close
    >
      <el-form label-width="80px">
        <el-form-item label="激励积分">
          <el-input-number v-model="creditBoostDelta" :min="1" :max="500" :step="10" style="width:160px" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="creditBoostRemark" placeholder="流失预警人工激励" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="creditBoostDialog = false">取消</el-button>
        <el-button type="primary" :loading="creditBoostLoading" @click="doCreditBoost">确认发放</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  Warning, Refresh, DataAnalysis, Calendar, Search,
  Connection, InfoFilled, TrendCharts, Trophy
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { predictChurnRisk, recommendForUser, hybridRecommend } from '@/api/ai'
import { getActivityList, getActivityDetail, getActivityRegistrations, sendInvitation } from '@/api/activity'
import { adminAdjustCredit } from '@/api/credit'

// ——— Tab ———
const activeTab = ref('overview')

// ——— 运营概览 ———
const overviewLoading = ref(false)
const riskList = ref([])
const activityList = ref([])

const highCount   = computed(() => riskList.value.filter(r => r.riskLevel === '高').length)
const midCount    = computed(() => riskList.value.filter(r => r.riskLevel === '中').length)
const lowCount    = computed(() => riskList.value.filter(r => r.riskLevel === '低').length)
const healthPercent = computed(() => {
  const total = riskList.value.length
  if (!total) return 100
  return Math.round(lowCount.value / total * 100)
})

const riskPercent = (level) => {
  const total = riskList.value.length
  if (!total) return 0
  const count = riskList.value.filter(r => r.riskLevel === level).length
  return Math.round(count / total * 100)
}

const activityStats = computed(() => {
  const list = activityList.value
  return [
    { label: '未开始', color: '#909399', count: list.filter(a => a.status === 0).length },
    { label: '报名中', color: '#409EFF', count: list.filter(a => a.status === 1).length },
    { label: '进行中', color: '#67c23a', count: list.filter(a => a.status === 2).length },
    { label: '已结束', color: '#c0c4cc', count: list.filter(a => a.status === 3).length }
  ]
})

const loadOverview = async () => {
  overviewLoading.value = true
  try {
    const [riskRes, actRes] = await Promise.all([
      predictChurnRisk(),
      getActivityList({ page: 1, size: 200 })
    ])
    riskList.value = riskRes.data || []
    activityList.value = actRes.data?.rows || []
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    overviewLoading.value = false
  }
}

// ——— 流失预警 Tab ———
const riskLoading = ref(false)
const filterLevel = ref('')

const filteredList = computed(() =>
  filterLevel.value
    ? riskList.value.filter(r => r.riskLevel === filterLevel.value)
    : riskList.value
)

const scoreColor = (score) => {
  if (score >= 0.65) return '#f56c6c'
  if (score >= 0.35) return '#e6a23c'
  return '#67c23a'
}

const loadRisk = async () => {
  riskLoading.value = true
  try {
    const res = await predictChurnRisk()
    riskList.value = res.data || []
  } catch (e) {
    // ignore
  } finally {
    riskLoading.value = false
  }
}

// ——— 推荐活动弹窗 ———
const recommendDialog = ref(false)
const recommendLoading = ref(false)
const recommendList = ref([])
const currentRiskUser = ref(null)
const registeringId = ref(null)

const openRecommendDialog = async (row) => {
  currentRiskUser.value = row
  recommendDialog.value = true
  recommendList.value = []
  recommendLoading.value = true
  try {
    const res = await recommendForUser(row.userId)
    recommendList.value = res.data || []
  } catch (e) {
    // ignore
  } finally {
    recommendLoading.value = false
  }
}

const doAdminRegister = async (activityId) => {
  registeringId.value = activityId
  try {
    await sendInvitation(activityId, currentRiskUser.value.userId)
    ElMessage.success('邀请已发送，志愿者将在消息中心看到邀请')
    recommendList.value = recommendList.value.filter(r => r.activityId !== activityId)
  } catch (e) {
    // ignore
  } finally {
    registeringId.value = null
  }
}

// ——— 活动诊断 Tab ———
const selectedActivityId = ref(null)
const diagnoseLoading = ref(false)
const diagnoseResult = ref(null)

const statusLabel = (s) => ['未开始', '报名中', '进行中', '已结束'][s] ?? '未知'
const statusTagType = (s) => ['info', 'primary', 'success', ''][s] ?? 'info'

const attendanceColor = (pct) => {
  if (pct >= 60) return '#67c23a'
  if (pct >= 30) return '#e6a23c'
  return '#f56c6c'
}

const runDiagnose = async () => {
  if (!selectedActivityId.value) return
  diagnoseLoading.value = true
  diagnoseResult.value = null
  try {
    const actDetail = await getActivityDetail(selectedActivityId.value)
    const activity = actDetail.data

    // requiredSkills 后端返回逗号分隔字符串，转为数组
    const rawSkills = activity.requiredSkills || ''
    const skills = rawSkills ? rawSkills.split(',').map(s => s.trim()).filter(Boolean) : []

    // 并发请求：报名名单 + 双阶段AI推荐
    const [regRes, hybridRes] = await Promise.all([
      getActivityRegistrations(selectedActivityId.value),
      skills.length
        ? hybridRecommend(null, selectedActivityId.value).catch(() => null)
        : Promise.resolve(null)
    ])

    const registrations = regRes.data || []
    const signed      = registrations.filter(r => r.status === 0).length
    const checkedIn   = registrations.filter(r => r.status === 2).length
    const cancelled   = registrations.filter(r => r.status === 1).length
    const absent      = registrations.filter(r => r.status === 4).length
    const total       = registrations.length
    const attendanceDenom = signed + checkedIn + absent
    const attendanceRate  = attendanceDenom > 0 ? Math.round(checkedIn / attendanceDenom * 100) : 0
    const cancelRate      = total > 0 ? Math.round(cancelled / total * 100) : 0

    diagnoseResult.value = {
      activity: {
        title: activity.title,
        status: activity.status,
        capacity: activity.totalQuota,
        registeredCount: activity.joinedQuota,
        remainingSlots: activity.remainQuota ?? 0,
        requiredSkills: skills
      },
      conversion: total > 0
        ? { signed, checkedIn, cancelled, absent, attendanceRate, cancelRate }
        : null,
      // 已报名用户 ID 集合，供候选人列表比对
      registeredUserIds: new Set(registrations.map(r => r.userId)),
      // 双阶段 AI 精推结果（Transformer 召回 + DeepSeek Agent 重排）
      hybridResult: hybridRes?.data || null
    }
  } catch (e) {
    // ignore
  } finally {
    diagnoseLoading.value = false
  }
}

// 诊断中已报名的用户集合（computed 方便模板使用）
const diagnoseRegisteredUserIds = computed(() =>
  diagnoseResult.value?.registeredUserIds || new Set()
)

// 诊断页邀请报名
const diagnoseInvitingId = ref(null)
const inviteFromDiagnose = async (userId, activityId) => {
  diagnoseInvitingId.value = userId
  try {
    await sendInvitation(activityId, userId)
    ElMessage.success('邀请已发送，等待志愿者确认')
    diagnoseResult.value.registeredUserIds.add(userId)
    diagnoseResult.value = { ...diagnoseResult.value }
  } catch (e) {
    // ignore
  } finally {
    diagnoseInvitingId.value = null
  }
}

// ——— 积分激励快捷弹窗（Tab2 流失预警行内）———
const creditBoostDialog = ref(false)
const creditBoostUser = ref(null)
const creditBoostDelta = ref(20)
const creditBoostRemark = ref('流失预警人工激励')
const creditBoostLoading = ref(false)

const openCreditBoostDialog = (row) => {
  creditBoostUser.value = row
  creditBoostDelta.value = 20
  creditBoostRemark.value = '流失预警人工激励'
  creditBoostDialog.value = true
}

const doCreditBoost = async () => {
  creditBoostLoading.value = true
  try {
    await adminAdjustCredit({
      userId: creditBoostUser.value.userId,
      delta: creditBoostDelta.value,
      remark: creditBoostRemark.value
    })
    ElMessage.success(`已为 ${creditBoostUser.value.nickname || '用户' + creditBoostUser.value.userId} 添加 ${creditBoostDelta.value} 积分`)
    creditBoostDialog.value = false
  } catch (e) {
    // ignore
  } finally {
    creditBoostLoading.value = false
  }
}

// ——— Tab4 积分管理 ———
const creditForm = ref({ userId: null, delta: 20, remark: '' })
const creditAdjusting = ref(false)

const doAdjustCredit = async () => {
  if (!creditForm.value.userId) { ElMessage.warning('请选择用户'); return }
  if (!creditForm.value.delta) { ElMessage.warning('请输入调整积分值'); return }
  creditAdjusting.value = true
  try {
    await adminAdjustCredit(creditForm.value)
    ElMessage.success('积分调整成功')
    creditForm.value = { userId: null, delta: 20, remark: '' }
  } catch (e) {
    // ignore
  } finally {
    creditAdjusting.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.admin-ai {
  padding: 0;
}

.main-tabs :deep(.el-tabs__content) {
  padding: 20px;
  background: #fff;
}

.section-card {
  border-radius: 10px;
  margin-bottom: 0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  font-size: 14px;
}

/* 统计卡片 */
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

/* 风险条形 */
.risk-bar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.risk-label {
  width: 48px;
  font-size: 13px;
  font-weight: bold;
  flex-shrink: 0;
}
.risk-count {
  width: 48px;
  font-size: 13px;
  color: #606266;
  text-align: right;
  flex-shrink: 0;
}
.red-text    { color: #f56c6c; }
.orange-text { color: #e6a23c; }
.green-text  { color: #67c23a; }

/* 活动状态小卡 */
.act-stat-item {
  background: #fafafa;
  border-left: 3px solid;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
}
.act-stat-num   { font-size: 28px; font-weight: bold; }
.act-stat-label { font-size: 12px; color: #909399; }

/* 诊断结果卡片等高 */
.diagnose-card { height: 100%; }

/* 推荐活动卡 */
.recommend-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  margin-bottom: 10px;
}
.recommend-card:hover { background: #f5f7fa; }
.recommend-card-left { flex: 1; margin-right: 12px; }
.recommend-title { font-weight: bold; font-size: 14px; }

/* 空状态提示 */
.empty-hint {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 16px 0;
}

/* AI 精推候选人小卡片 */
.hybrid-candidate {
  background: #faf5ff;
  border: 1px solid #e9d5ff;
  border-radius: 8px;
  padding: 8px 10px;
  text-align: center;
}
.hybrid-candidate.hybrid-top {
  background: #f3e8ff;
  border-color: #7c3aed;
  box-shadow: 0 2px 8px rgba(124,58,237,0.15);
}
.hybrid-rank {
  font-size: 16px;
  font-weight: bold;
  color: #7c3aed;
  margin-bottom: 4px;
}
.hybrid-name {
  font-size: 12px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
