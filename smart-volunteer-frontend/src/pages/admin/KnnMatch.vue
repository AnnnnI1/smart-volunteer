<template>
  <div>
    <el-card class="page-card" shadow="never">
      <template #header>
        <span class="card-title"><el-icon><Connection /></el-icon> KNN 志愿者智能推荐</span>
      </template>

      <el-row :gutter="24">
        <!-- 左：输入参数 -->
        <el-col :span="8">
          <div class="input-panel">
            <div class="panel-label">活动所需技能</div>
            <div class="skill-tags">
              <el-tag
                v-for="(s, i) in skillList"
                :key="i"
                closable
                @close="skillList.splice(i, 1)"
                type="primary"
                effect="light"
                style="margin: 4px"
              >{{ s }}</el-tag>
            </div>
            <div style="display:flex;gap:8px;margin-top:8px">
              <el-input v-model="newSkill" placeholder="输入技能标签" size="small" @keyup.enter="addSkill" clearable />
              <el-button size="small" type="primary" @click="addSkill">添加</el-button>
            </div>

            <div style="margin-top:12px">
              <div class="panel-label">常用技能快速选择</div>
              <el-tag
                v-for="s in presetSkills"
                :key="s"
                :type="skillList.includes(s) ? 'primary' : 'info'"
                effect="plain"
                style="cursor:pointer;margin:4px"
                @click="toggleSkill(s)"
              >{{ s }}</el-tag>
            </div>

            <el-divider />

            <div class="panel-label">返回数量 Top-K</div>
            <el-slider v-model="topK" :min="1" :max="10" show-input size="small" />

            <el-button
              type="primary"
              :loading="loading"
              @click="handleMatch"
              style="width:100%;margin-top:16px"
              :disabled="skillList.length === 0"
            >
              <el-icon><Search /></el-icon> 开始匹配
            </el-button>
          </div>
        </el-col>

        <!-- 右：匹配结果 -->
        <el-col :span="16">
          <div v-if="!results.length && !loading" class="empty-hint">
            <el-empty description="输入活动所需技能后点击匹配">
              <template #image>
                <el-icon style="font-size:80px;color:#c0c4cc"><UserFilled /></el-icon>
              </template>
            </el-empty>
          </div>

          <div v-loading="loading">
            <div class="result-header" v-if="results.length">
              <span>匹配结果</span>
              <el-tag type="success">共 {{ results.length }} 位志愿者</el-tag>
              <span style="font-size:12px;color:#909399;margin-left:8px">可查看档案或直接邀请报名</span>
            </div>

            <div v-for="(vol, index) in results" :key="vol.userId" class="vol-card">
              <!-- 排名徽章 -->
              <div class="rank-badge" :class="rankClass(index)">{{ index + 1 }}</div>

              <div class="vol-info">
                <div class="vol-name">
                  {{ vol.realName || `用户${vol.userId}` }}
                  <span class="vol-id">ID: {{ vol.userId }}</span>
                  <el-tooltip v-if="vol.isColdStart" content="该志愿者暂无历史记录，综合评分已用均值先验平滑" placement="top">
                    <el-tag size="small" effect="dark" class="cold-start-tag">新人</el-tag>
                  </el-tooltip>
                </div>

                <div style="margin: 6px 0">
                  <el-tag
                    v-for="skill in (vol.skills || '').split(',')"
                    :key="skill"
                    :type="skillList.includes(skill) ? 'success' : 'info'"
                    size="small"
                    effect="light"
                    style="margin:2px"
                  >{{ skill }}</el-tag>
                </div>

                <div style="font-size:12px;color:#909399">
                  累计服务：{{ vol.totalHours }} 小时
                  &nbsp;·&nbsp;
                  <span :style="{ color: creditColor(vol.creditBalance) }">
                    {{ medalIcon(vol.creditBalance) }} 积分 {{ vol.creditBalance ?? 0 }}
                  </span>
                  &nbsp;·&nbsp;
                  <span :style="{ color: attendanceColor(vol.attendanceRate) }">
                    出勤率：{{ vol.attendanceRate != null ? Math.round(vol.attendanceRate * 100) + '%' : 'N/A' }}
                  </span>
                </div>
              </div>

              <!-- 综合得分 -->
              <div class="similarity-box">
                <div class="similarity-num">{{ (vol.finalScore * 100).toFixed(1) }}%</div>
                <el-progress
                  :percentage="Math.min(vol.finalScore * 80, 100)"
                  :color="similarityColor(vol.similarity)"
                  :stroke-width="10"
                  :show-text="false"
                  style="width:100px"
                />
                <div style="font-size:11px;color:#909399;margin-top:2px">综合评分</div>
                <div style="font-size:10px;color:#c0c4cc">
                  技能{{ (vol.similarity * 100).toFixed(0) }}%
                  + 积分{{ (vol.creditScore * 100).toFixed(0) }}%
                  + 出勤{{ (vol.attendanceScore * 100).toFixed(0) }}%
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="action-btns">
                <el-button size="small" type="primary" plain @click="viewProfile(vol)">
                  <el-icon><User /></el-icon> 查看档案
                </el-button>
                <el-button size="small" type="success" @click="openInvite(vol)">
                  <el-icon><Plus /></el-icon> 邀请报名
                </el-button>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 查看档案弹窗 -->
    <el-dialog v-model="profileVisible" :title="`${currentVol?.realName || ''} 的志愿者档案`" width="460px">
      <div v-if="profileLoading" style="text-align:center;padding:30px">
        <el-icon class="is-loading" style="font-size:32px"><Loading /></el-icon>
      </div>
      <el-descriptions v-else :column="2" border size="small">
        <el-descriptions-item label="姓名">{{ currentProfile?.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentVol?.userId }}</el-descriptions-item>
        <el-descriptions-item label="累计服务时长" :span="2">
          <span :style="{ color: (currentVol?.totalHours || 0) >= 20 ? '#67c23a' : '#e6a23c' }">
            {{ currentVol?.totalHours || 0 }} 小时
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="积分 / 勋章" :span="2">
          <span :style="{ color: creditColor(currentVol?.creditBalance), fontWeight: 'bold' }">
            {{ medalIcon(currentVol?.creditBalance) }} {{ currentVol?.creditBalance ?? 0 }} 分
          </span>
          &nbsp;
          <el-tag size="small" :type="medalTagType(currentVol?.creditBalance)">
            {{ medalLabel(currentVol?.creditBalance) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="技能档案" :span="2">
          <el-tag
            v-for="s in (currentVol?.skills || '').split(',')"
            :key="s"
            :type="skillList.includes(s) ? 'success' : 'info'"
            size="small"
            effect="light"
            style="margin:2px"
          >{{ s }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="匹配度" :span="2">
          <el-progress
            :percentage="Math.round((currentVol?.similarity || 0) * 100)"
            :color="similarityColor(currentVol?.similarity || 0)"
            :stroke-width="12"
          />
        </el-descriptions-item>
        <el-descriptions-item v-if="currentVol?.isColdStart" label="冷启动" :span="2">
          <el-tag type="info" effect="dark" size="small" class="cold-start-tag">新人</el-tag>
          <span style="font-size:12px;color:#909399;margin-left:8px">暂无历史数据，评分已用均值先验平滑</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="profileVisible = false">关闭</el-button>
        <el-button type="success" @click="openInvite(currentVol); profileVisible = false">
          邀请报名
        </el-button>
      </template>
    </el-dialog>

    <!-- 邀请报名弹窗 -->
    <el-dialog v-model="inviteVisible" :title="`邀请 ${inviteVol?.realName || ''} 报名活动`" width="500px">
      <div style="margin-bottom:12px;font-size:13px;color:#606266">
        选择要邀请该志愿者参加的活动（仅显示报名中的活动）：
      </div>
      <div v-loading="activitiesLoading">
        <div
          v-for="act in recruitingActivities"
          :key="act.id"
          class="activity-item"
          :class="{ selected: selectedActivityId === act.id }"
          @click="selectedActivityId = act.id"
        >
          <div style="font-weight:bold;color:#303133">{{ act.title }}</div>
          <div style="font-size:12px;color:#909399;margin-top:4px;display:flex;gap:12px">
            <span>剩余名额：<b :style="{ color: act.remainQuota > 0 ? '#67c23a' : '#f56c6c' }">{{ act.remainQuota }}</b></span>
            <span>开始：{{ act.startTime?.substring(0, 10) }}</span>
          </div>
          <div v-if="act.requiredSkills" style="margin-top:4px">
            <el-tag v-for="s in act.requiredSkills.split(',')" :key="s" size="small" effect="plain" style="margin:2px">{{ s }}</el-tag>
          </div>
        </div>
        <el-empty v-if="!activitiesLoading && !recruitingActivities.length" description="当前没有报名中的活动" />
      </div>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button
          type="success"
          :loading="inviting"
          :disabled="!selectedActivityId"
          @click="confirmInvite"
        >确认邀请报名</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Search, UserFilled, User, Plus, Loading } from '@element-plus/icons-vue'
import { knnMatch } from '@/api/ai'
import { getActivityList, sendInvitation } from '@/api/activity'
import { getByUserId } from '@/api/profile'

const skillList = ref([])
const newSkill  = ref('')
const topK      = ref(5)
const loading   = ref(false)
const results   = ref([])

// 查看档案
const profileVisible = ref(false)
const profileLoading = ref(false)
const currentVol     = ref(null)
const currentProfile = ref(null)

// 邀请报名
const inviteVisible        = ref(false)
const inviteVol            = ref(null)
const recruitingActivities = ref([])
const activitiesLoading    = ref(false)
const selectedActivityId   = ref(null)
const inviting             = ref(false)

const presetSkills = ['医疗', '急救', '教育', '翻译', '社区服务', '文化', '体育', '法律', '心理', '环保']

const addSkill = () => {
  const s = newSkill.value.trim()
  if (s && !skillList.value.includes(s)) skillList.value.push(s)
  newSkill.value = ''
}
const toggleSkill = (s) => {
  const idx = skillList.value.indexOf(s)
  if (idx >= 0) skillList.value.splice(idx, 1)
  else skillList.value.push(s)
}

const handleMatch = async () => {
  if (!skillList.value.length) { ElMessage.warning('请至少输入一个技能'); return }
  loading.value = true
  results.value = []
  try {
    const res = await knnMatch({ requiredSkills: skillList.value, topK: topK.value })
    results.value = res.data || []
    if (!results.value.length) ElMessage.info('未找到匹配的志愿者，请先让志愿者完善技能档案')
  } finally {
    loading.value = false
  }
}

// 查看档案
const viewProfile = async (vol) => {
  currentVol.value     = vol
  currentProfile.value = null
  profileVisible.value = true
  profileLoading.value = true
  try {
    const res = await getByUserId(vol.userId)
    currentProfile.value = res.data
  } finally {
    profileLoading.value = false
  }
}

// 打开邀请弹窗
const openInvite = async (vol) => {
  inviteVol.value          = vol
  selectedActivityId.value = null
  recruitingActivities.value = []
  inviteVisible.value      = true
  activitiesLoading.value  = true
  try {
    const res = await getActivityList({ status: 1, page: 1, size: 50 })
    recruitingActivities.value = (res.data?.rows || []).filter(a => a.remainQuota > 0)
  } finally {
    activitiesLoading.value = false
  }
}

// 确认邀请
const confirmInvite = async () => {
  if (!selectedActivityId.value || !inviteVol.value) return
  inviting.value = true
  try {
    await sendInvitation(selectedActivityId.value, inviteVol.value.userId)
    const actTitle = recruitingActivities.value.find(a => a.id === selectedActivityId.value)?.title || ''
    ElMessage.success(`邀请已发送给 ${inviteVol.value.realName}，等待其确认「${actTitle}」`)
    inviteVisible.value = false
  } finally {
    inviting.value = false
  }
}

const rankClass       = (i) => ({ 0: 'rank-gold', 1: 'rank-silver', 2: 'rank-bronze' }[i] || 'rank-normal')

// 勋章辅助函数
const medalIcon      = (credit) => credit >= 500 ? '🥇' : credit >= 200 ? '🥈' : credit >= 50 ? '🥉' : ''
const medalLabel     = (credit) => credit >= 500 ? '金牌志愿者' : credit >= 200 ? '银牌志愿者' : credit >= 50 ? '铜牌志愿者' : '普通志愿者'
const medalTagType   = (credit) => credit >= 500 ? 'warning' : credit >= 200 ? '' : credit >= 50 ? 'info' : 'info'
const creditColor    = (credit) => credit >= 500 ? '#e6a23c' : credit >= 200 ? '#909399' : credit >= 50 ? '#b87333' : '#c0c4cc'
const attendanceColor = (rate) => {
  if (rate == null) return '#c0c4cc'
  if (rate >= 0.8) return '#67c23a'
  if (rate >= 0.5) return '#e6a23c'
  return '#f56c6c'
}
const similarityColor = (s) => {
  if (s >= 0.8) return '#67c23a'
  if (s >= 0.5) return '#e6a23c'
  return '#909399'
}
</script>

<style scoped>
.page-card { border-radius: 10px; }

.card-title {
  font-size: 16px; font-weight: bold;
  display: flex; align-items: center; gap: 6px;
}

.input-panel {
  background: #f8f9fa; padding: 20px; border-radius: 10px;
}

.panel-label {
  font-size: 13px; font-weight: bold; color: #606266; margin-bottom: 8px;
}

.skill-tags { min-height: 36px; }
.empty-hint { padding: 40px 0; }

.result-header {
  display: flex; align-items: center; gap: 10px;
  font-size: 15px; font-weight: bold; margin-bottom: 16px;
}

.vol-card {
  display: flex; align-items: center;
  background: #fff; border: 1px solid #e4e7ed;
  border-radius: 10px; padding: 16px; margin-bottom: 12px;
  transition: box-shadow 0.2s;
  gap: 12px;
}
.vol-card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.1); }

.rank-badge {
  width: 32px; height: 32px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: bold; font-size: 14px; flex-shrink: 0;
}
.rank-gold   { background: #ffd700; color: #333; }
.rank-silver { background: #c0c0c0; color: #333; }
.rank-bronze { background: #cd7f32; color: #fff; }
.rank-normal { background: #e4e7ed; color: #606266; }

.vol-info { flex: 1; }
.vol-name { font-size: 15px; font-weight: bold; color: #303133; }
.vol-id   { font-size: 12px; color: #909399; font-weight: normal; margin-left: 8px; }

.similarity-box { text-align: center; }
.similarity-num { font-size: 20px; font-weight: bold; color: #409EFF; }

.action-btns {
  display: flex; flex-direction: column; gap: 6px; flex-shrink: 0;
}

.activity-item {
  border: 2px solid #e4e7ed; border-radius: 8px;
  padding: 12px 14px; margin-bottom: 8px; cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.activity-item:hover { border-color: #409EFF; background: #f0f7ff; }
.activity-item.selected { border-color: #67c23a; background: #f0faf0; }

.cold-start-tag {
  margin-left: 6px;
  font-size: 11px;
  background: #909399;
  border-color: #909399;
  vertical-align: middle;
}
</style>
