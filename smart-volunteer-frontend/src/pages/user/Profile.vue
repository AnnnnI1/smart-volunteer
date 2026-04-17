<template>
  <div>
    <!-- 角色升级提示 -->
    <el-alert
      v-if="roleUpgraded"
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom:16px"
    >
      <template #title>
        恭喜！您已被升级为<b>组织者</b>，重新登录后菜单将更新。
        <el-button type="success" size="small" style="margin-left:12px" @click="relogin">立即重新登录</el-button>
      </template>
    </el-alert>

    <el-row :gutter="20">
      <!-- 左：基本信息 + 档案 -->
      <el-col :span="14">
        <!-- 基本信息 -->
        <el-card class="profile-card" shadow="never">
          <template #header>
            <span class="card-title"><el-icon><User /></el-icon> 基本信息</span>
          </template>
          <div class="user-info-box">
            <!-- 可点击头像 -->
            <div class="avatar-wrapper" @click="triggerAvatarUpload" title="点击更换头像">
              <el-avatar :size="80" :src="avatarSrc" icon="UserFilled" />
              <div class="avatar-mask">
                <el-icon :size="20" color="#fff"><Camera /></el-icon>
                <div style="font-size:11px;margin-top:2px">更换</div>
              </div>
              <div v-if="uploadingAvatar" class="avatar-spin">
                <el-icon class="is-loading" :size="28" color="#fff"><Loading /></el-icon>
              </div>
              <!-- 勋章徽章：右下角覆盖 -->
              <div v-if="medalInfo.icon" class="avatar-medal-badge" :title="medalInfo.label">
                {{ medalInfo.icon }}
              </div>
            </div>
            <!-- 隐藏 file input -->
            <input
              ref="avatarInputRef"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              style="display:none"
              @change="onAvatarFileChange"
            />

            <div style="flex:1">
              <el-form :model="basicForm" label-width="80px" :disabled="!editingBasic">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="用户名">
                      <el-input v-model="basicForm.username" disabled />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="昵称">
                      <el-input v-model="basicForm.nickname" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="邮箱">
                      <el-input v-model="basicForm.email" placeholder="选填" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="手机">
                      <el-input v-model="basicForm.phone" placeholder="选填" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-form>
              <div style="padding-left:80px;display:flex;gap:8px;flex-wrap:wrap">
                <el-button v-if="!editingBasic" type="primary" plain size="small" @click="editingBasic = true">编辑信息</el-button>
                <template v-else>
                  <el-button type="primary" size="small" :loading="saving" @click="saveBasic">保存</el-button>
                  <el-button size="small" @click="cancelEditBasic">取消</el-button>
                </template>
                <el-button type="warning" plain size="small" @click="pwdDialogVisible = true">
                  <el-icon><Lock /></el-icon>&nbsp;修改密码
                </el-button>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 志愿者档案 -->
        <el-card class="profile-card" shadow="never" style="margin-top:16px">
          <template #header>
            <span class="card-title"><el-icon><Medal /></el-icon> 志愿者档案</span>
          </template>

          <el-form :model="profileForm" label-width="80px" :disabled="!editingProfile">
            <el-form-item label="真实姓名">
              <el-input v-model="profileForm.realName" placeholder="请填写真实姓名" style="max-width:200px"/>
            </el-form-item>

            <el-form-item label="技能标签">
              <div>
                <el-tag
                  v-for="(skill, i) in profileForm.skillList"
                  :key="i"
                  :closable="editingProfile"
                  @close="removeSkill(i)"
                  style="margin: 4px"
                  type="primary"
                  effect="light"
                >{{ skill }}</el-tag>
                <template v-if="editingProfile">
                  <el-input
                    v-if="addingSkill"
                    v-model="newSkill"
                    size="small"
                    style="width:100px;margin:4px"
                    @keyup.enter="confirmSkill"
                    @blur="confirmSkill"
                    ref="skillInputRef"
                    placeholder="回车确认"
                  />
                  <el-button v-else size="small" @click="startAddSkill" style="margin:4px">+ 添加技能</el-button>
                </template>
                <el-empty v-if="profileForm.skillList.length === 0 && !editingProfile"
                  description="暂无技能标签" :image-size="60" />
              </div>
            </el-form-item>
          </el-form>

          <div style="padding-left:80px">
            <el-tag type="info" style="margin-bottom:12px">
              累计服务时长：{{ profileForm.totalHours }} 小时
            </el-tag><br>
            <el-button v-if="!editingProfile" type="success" plain size="small" @click="editingProfile = true">编辑档案</el-button>
            <template v-else>
              <el-button type="success" size="small" :loading="savingProfile" @click="saveProfile">保存档案</el-button>
              <el-button size="small" @click="editingProfile = false">取消</el-button>
            </template>
          </div>
        </el-card>
      </el-col>

      <!-- 右：数据统计 + 申请组织者 -->
      <el-col :span="10">
        <el-card class="profile-card" shadow="never">
          <template #header>
            <span class="card-title"><el-icon><DataAnalysis /></el-icon> 我的数据</span>
          </template>
          <el-row :gutter="12">
            <el-col :span="12">
              <div class="stat-box blue clickable" @click="allRegsDialog = true" title="点击查看详情">
                <div class="stat-num">{{ stats.total }}</div>
                <div class="stat-label">累计报名 <el-icon style="font-size:11px;vertical-align:middle"><View /></el-icon></div>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="stat-box green clickable" @click="activeRegsDialog = true" title="点击查看详情">
                <div class="stat-num">{{ stats.active }}</div>
                <div class="stat-label">有效报名 <el-icon style="font-size:11px;vertical-align:middle"><View /></el-icon></div>
              </div>
            </el-col>
            <el-col :span="12" style="margin-top:12px">
              <div class="stat-box orange">
                <div class="stat-num">{{ profileForm.totalHours }}</div>
                <div class="stat-label">服务时长(h)</div>
              </div>
            </el-col>
            <el-col :span="12" style="margin-top:12px">
              <div class="stat-box purple clickable" @click="skillsDialog = true" title="点击查看技能">
                <div class="stat-num">{{ profileForm.skillList.length }}</div>
                <div class="stat-label">技能数量 <el-icon style="font-size:11px;vertical-align:middle"><View /></el-icon></div>
              </div>
            </el-col>
            <el-col :span="24" style="margin-top:12px">
              <!-- 等级面板 -->
              <div class="level-panel clickable" @click="creditDialog = true" title="点击查看积分流水">
                <!-- 顶部：等级图标 + 名称 + 当前积分 -->
                <div class="level-header">
                  <div class="level-icon-wrap">
                    <span v-if="medalInfo.icon" class="level-icon">{{ medalInfo.icon }}</span>
                    <span v-else class="level-icon level-icon-empty">○</span>
                  </div>
                  <div class="level-title-wrap">
                    <div class="level-title" :style="{ color: medalInfo.color }">{{ medalInfo.label }}</div>
                    <div class="level-score">{{ creditBalance }} 分</div>
                  </div>
                </div>

                <!-- 进度条区域 -->
                <div class="level-progress-wrap">
                  <el-progress
                    :percentage="medalInfo.next > 0 ? Math.min(creditBalance / medalInfo.next * 100, 100) : 100"
                    :color="medalInfo.next > 0 ? medalInfo.color : '#e6a23c'"
                    :stroke-width="12"
                    :show-text="false"
                    style="flex:1"
                  />
                  <span class="level-progress-label">
                    {{ medalInfo.next > 0 ? `${creditBalance} / ${medalInfo.next}` : '已满级' }}
                  </span>
                </div>

                <!-- 底部提示 -->
                <div class="level-hint">
                  <template v-if="medalInfo.next > 0">
                    <span>距</span>
                    <span :style="{ color: medalInfo.color, fontWeight: 'bold' }">{{ medalInfo.nextLabel }}</span>
                    <span>还差 <b>{{ medalInfo.next - creditBalance }}</b> 分</span>
                    <span class="level-hint-action" @click.stop="router.push('/homepage/activities')">→ 去参与活动</span>
                  </template>
                  <template v-else>
                    <span style="color:#e6a23c;font-weight:bold">🎉 已达最高等级，感谢您的贡献！</span>
                  </template>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 申请成为组织者（仅志愿者可见） -->
        <el-card v-if="userInfo?.role === 1" class="profile-card organizer-apply-card" shadow="never" style="margin-top:16px">
          <template #header>
            <span class="card-title"><el-icon><Star /></el-icon> 成为组织者</span>
          </template>
          <div style="font-size:13px;color:#606266;line-height:1.8;margin-bottom:14px">
            组织者可以<b>发起活动</b>、<b>管理报名名单</b>、<b>生成签到码</b>，同时保留全部志愿者权益。
            <br>
            <span style="color:#909399">（组织者仍可正常报名参与其他活动）</span>
          </div>
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:12px">
            <el-tag type="warning" effect="light">发起活动</el-tag>
            <el-tag type="success" effect="light">管理报名</el-tag>
            <el-tag type="primary" effect="light">签到管理</el-tag>
            <el-tag type="info"    effect="light">保留志愿者身份</el-tag>
          </div>
          <el-alert
            v-if="applied"
            title="申请审核中，请等待管理员处理。审核通过后重新登录即可生效。"
            type="warning"
            :closable="false"
            style="margin-bottom:10px"
          />
          <el-alert
            v-if="rejected"
            title="您的组织者申请已被拒绝，可重新提交申请。"
            type="error"
            :closable="true"
            @close="rejected = false"
            style="margin-bottom:10px"
          />
          <el-button
            v-if="!applied"
            type="warning"
            @click="applyDialogVisible = true"
            style="width:100%"
          >
            <el-icon><Star /></el-icon>&nbsp; 申请成为组织者
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 申请成为组织者弹窗 -->
    <el-dialog v-model="applyDialogVisible" title="申请成为组织者" width="520px" align-center @close="applyErrorMsg = ''; applyReason = ''">
      <div style="line-height:1.9;font-size:14px;color:#303133">
        <p>
          <b>申请流程：</b>
        </p>
        <el-steps direction="vertical" :active="3" style="margin:8px 0 16px 8px">
          <el-step title="填写申请理由" description="描述您想成为组织者的动机和能力" />
          <el-step title="AI 资质评估" description="系统将基于您的历史数据生成 AI 尽调报告" />
          <el-step title="管理员审核" description="管理员在用户管理页面综合 AI 报告进行人工复核" />
          <el-step title="成为组织者" description="审核通过后重新登录即可使用组织者功能" />
        </el-steps>
        <el-alert
          title="您的账号信息"
          type="info"
          :closable="false"
          style="margin-bottom:10px"
        >
          <template #default>
            用户名：<b>{{ userInfo?.username }}</b>
            &nbsp;&nbsp; 昵称：<b>{{ userInfo?.nickname }}</b>
          </template>
        </el-alert>
        <el-form-item label="申请理由" style="margin-top:12px">
          <el-input
            v-model="applyReason"
            type="textarea"
            :rows="4"
            placeholder="请详细描述您想成为组织者的原因、相关经验及能够为志愿者带来的价值（至少10个字）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </div>
      <el-alert
        v-if="applyErrorMsg"
        :title="applyErrorMsg"
        type="error"
        :closable="false"
        show-icon
        style="margin-top:10px"
      />
      <template #footer>
        <el-button @click="applyDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="applying" @click="handleApplyOrganizer">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->    <el-dialog      v-model="pwdDialogVisible"
      title="修改密码"
      width="400px"
      :close-on-click-modal="false"
      @close="resetPwdForm"
    >
      <el-form :model="pwdForm" label-width="90px" ref="pwdFormRef">
        <el-form-item label="旧密码" prop="oldPassword"
          :rules="[{ required: true, message: '请输入旧密码' }]">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword"
          :rules="[{ required: true, min: 5, message: '至少5位' }]">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPwd"
          :rules="[{ required: true, validator: checkConfirm }]">
          <el-input v-model="pwdForm.confirmPwd" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="changingPwd" @click="changePassword">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 累计报名 详情弹窗 -->
    <el-dialog v-model="allRegsDialog" title="累计报名记录" width="580px">
      <el-empty v-if="!regRows.length" description="暂无报名记录" />
      <el-table v-else :data="regRows" size="small" max-height="400">
        <el-table-column label="#" type="index" width="48" />
        <el-table-column label="活动" min-width="120">
          <template #default="{row}">
            <el-button link type="primary" @click="allRegsDialog=false; router.push(`/homepage/activity/${row.activityId}`)">
              活动 #{{ row.activityId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{row}">
            <el-tag :type="regStatusType(row.status)" size="small">{{ regStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" min-width="140">
          <template #default="{row}">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="allRegsDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 有效报名 详情弹窗 -->
    <el-dialog v-model="activeRegsDialog" title="有效报名记录" width="580px">
      <el-empty v-if="!activeRegs.length" description="暂无有效报名" />
      <el-table v-else :data="activeRegs" size="small" max-height="400">
        <el-table-column label="#" type="index" width="48" />
        <el-table-column label="活动" min-width="120">
          <template #default="{row}">
            <el-button link type="primary" @click="activeRegsDialog=false; router.push(`/homepage/activity/${row.activityId}`)">
              活动 #{{ row.activityId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default>
            <el-tag type="success" size="small">报名成功</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报名时间" min-width="140">
          <template #default="{row}">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="activeRegsDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 技能数量 详情弹窗 -->
    <el-dialog v-model="skillsDialog" title="我的技能档案" width="420px">
      <div v-if="profileForm.skillList.length" style="padding:8px 0">
        <el-tag
          v-for="s in profileForm.skillList"
          :key="s"
          type="primary"
          effect="light"
          style="margin:6px;font-size:14px;padding:6px 16px"
        >{{ s }}</el-tag>
      </div>
      <el-empty v-else description="还未添加技能" :image-size="80" />
      <template #footer>
        <el-button @click="skillsDialog = false">关闭</el-button>
        <el-button type="success" @click="skillsDialog=false; editingProfile=true">编辑技能</el-button>
      </template>
    </el-dialog>

    <!-- 积分流水弹窗 -->
    <el-dialog v-model="creditDialog" title="我的积分流水" width="600px">
      <div style="margin-bottom:12px;display:flex;align-items:center;gap:12px">
        <span style="font-size:15px;color:#606266">当前积分：</span>
        <span style="font-size:28px;font-weight:bold;color:#e6a23c">{{ creditBalance }}</span>
        <el-tag type="warning" effect="light">分</el-tag>
      </div>
      <el-empty v-if="!creditRecords.length" description="暂无积分记录" />
      <el-table v-else :data="creditRecords" size="small" max-height="360">
        <el-table-column label="类型" width="100">
          <template #default="{row}">
            <el-tag :type="creditTypeColor(row.changeType)" size="small">{{ creditTypeLabel(row.changeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="积分变化" width="90">
          <template #default="{row}">
            <span :style="{color: row.points > 0 ? '#67c23a' : '#f56c6c', fontWeight:'bold'}">
              {{ row.points > 0 ? '+' + row.points : row.points }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="balanceAfter" label="余额" width="80" />
        <el-table-column label="关联活动" min-width="80">
          <template #default="{row}">
            <el-button v-if="row.activityId" link type="primary" size="small"
              @click="creditDialog=false; router.push(`/homepage/activity/${row.activityId}`)">
              #{{ row.activityId }}
            </el-button>
            <span v-else style="color:#909399">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" />
        <el-table-column label="时间" min-width="130">
          <template #default="{row}">{{ fmtTime(row.createTime) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="creditDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Medal, DataAnalysis, Lock, Camera, Loading, View, Trophy, Star } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import { userUpdateInfo, changePassword as changePwd, uploadAvatar, applyOrganizer, getUserMe } from '@/api/user'
import { getMyProfile, updateProfile } from '@/api/profile'
import { getMyRegistrations } from '@/api/activity'
import { getCreditBalance, getCreditRecords } from '@/api/credit'

const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const router = useRouter()

// ── 头像 ──────────────────────────────────────────────────────────
const avatarInputRef = ref(null)
const uploadingAvatar = ref(false)
// 用本地 blob 预览或 store 里的 URL
const avatarSrc = computed(() => authStore.userInfo?.avatar || '')

const triggerAvatarUpload = () => {
  avatarInputRef.value?.click()
}

const onAvatarFileChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  // 重置 input，允许重复选同一文件
  e.target.value = ''

  const allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
  if (!allowed.includes(file.type)) {
    ElMessage.error('仅支持 JPG、PNG、GIF、WEBP 格式')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return
  }

  uploadingAvatar.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadAvatar(formData)
    // res.data 是 OSS 返回的 URL
    authStore.updateUserInfo({ ...authStore.userInfo, avatar: res.data })
    ElMessage.success('头像更新成功')
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    uploadingAvatar.value = false
  }
}

// ── 基本信息 ────────────────────────────────────────────────────────
const editingBasic = ref(false)
const saving = ref(false)
let basicFormSnapshot = {}
const basicForm = reactive({ username: '', nickname: '', email: '', phone: '' })

// ── 档案 ────────────────────────────────────────────────────────────
const editingProfile = ref(false)
const savingProfile = ref(false)
const addingSkill = ref(false)
const newSkill = ref('')
const skillInputRef = ref(null)
const profileForm = reactive({ realName: '', skillList: [], totalHours: 0 })

// ── 统计 ────────────────────────────────────────────────────────────
const stats = reactive({ total: 0, active: 0 })
const regRows = ref([])

// ── 统计详情弹窗 ─────────────────────────────────────────────────────
const allRegsDialog    = ref(false)
const activeRegsDialog = ref(false)
const skillsDialog     = ref(false)

const activeRegs   = computed(() => regRows.value.filter(r => r.status === 0))
const regStatusLabel = (s) => ({ 0: '报名成功', 1: '已取消', 2: '已完成' }[s] ?? '-')
const regStatusType  = (s) => ({ 0: 'success', 1: 'info', 2: 'primary' }[s] ?? 'info')
const fmtTime = (t) => t ? String(t).replace('T', ' ').substring(0, 16) : '-'

// ── 积分 ─────────────────────────────────────────────────────────────
const creditBalance = ref(0)
const creditRecords = ref([])
const creditDialog  = ref(false)

// 勋章等级：铜≥50 / 银≥200 / 金≥500
const medalInfo = computed(() => {
  const b = creditBalance.value
  if (b >= 500) return { icon: '🥇', label: '金牌志愿者', color: '#e6a23c', next: 0,   nextLabel: '' }
  if (b >= 200) return { icon: '🥈', label: '银牌志愿者', color: '#909399', next: 500,  nextLabel: '金牌' }
  if (b >= 50)  return { icon: '🥉', label: '铜牌志愿者', color: '#b87333', next: 200,  nextLabel: '银牌' }
  return               { icon: '',   label: '普通志愿者', color: '#c0c4cc', next: 50,   nextLabel: '铜牌' }
})

const creditTypeLabel = (t) => ({ 1: '报名奖励', 2: '完成奖励', 3: '取消扣分', 4: '管理员调整' }[t] ?? '-')
const creditTypeColor = (t) => ({ 1: 'success', 2: 'primary', 3: 'danger', 4: 'warning' }[t] ?? 'info')

const fetchCredit = async () => {
  try {
    const [balRes, recRes] = await Promise.all([
      getCreditBalance(),
      getCreditRecords({ page: 1, size: 50 })
    ])
    creditBalance.value = balRes.data?.balance ?? 0
    creditRecords.value = recRes.data?.rows || []
  } catch (e) { /* credit服务未启动时静默降级 */ }
}

// ── 密码弹窗 ─────────────────────────────────────────────────────────
const pwdDialogVisible   = ref(false)
const applyDialogVisible = ref(false)
const applying           = ref(false)
const applied            = computed(() => authStore.userInfo?.applyOrganizer === 1)
const rejected           = ref(false)
const applyErrorMsg      = ref('')
const applyReason        = ref('')

const handleApplyOrganizer = async () => {
  if (!applyReason.value.trim()) {
    applyErrorMsg.value = '请填写申请理由'
    return
  }
  if (applyReason.value.trim().length < 10) {
    applyErrorMsg.value = '申请理由请至少填写10个字'
    return
  }
  applying.value = true
  applyErrorMsg.value = ''
  try {
    await applyOrganizer({ applyReason: applyReason.value.trim() })
    authStore.updateUserInfo({ ...authStore.userInfo, applyOrganizer: 1, auditStatus: 0 })
    rejected.value = false
    applyDialogVisible.value = false
    ElMessage.success('您的申请已提交，正在进行AI资质评估与人工复核，请等待管理员审核')
  } catch (e) {
    applyErrorMsg.value = e?.msg || '申请失败，请稍后重试'
  } finally {
    applying.value = false
  }
}
const pwdFormRef = ref(null)
const changingPwd = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPwd: '' })

// ── 角色升级检测 ──────────────────────────────────────────────────────
const roleUpgraded = ref(false)

const checkMyStatus = async () => {
  try {
    const res = await getUserMe()
    const dbUser = res.data
    if (!dbUser) return
    const current = authStore.userInfo || {}
    // 曾经申请过（store里是1），但DB已清零且role还是志愿者 → 被拒绝了
    if (current.applyOrganizer === 1 && dbUser.applyOrganizer === 0 && dbUser.role === 1) {
      rejected.value = true
    }
    authStore.updateUserInfo({ ...current, applyOrganizer: dbUser.applyOrganizer })
    if (dbUser.role === 2 && current.role === 1) {
      roleUpgraded.value = true
    }
  } catch (e) {
    console.error('[checkMyStatus] 请求失败:', e)
  }
}

const relogin = () => {
  authStore.logout()
  router.push('/login')
}

const checkConfirm = (rule, value, cb) => {
  value !== pwdForm.newPassword ? cb(new Error('两次密码不一致')) : cb()
}
const resetPwdForm = () => {
  pwdFormRef.value?.resetFields()
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPwd: '' })
}

// ── 初始化 ────────────────────────────────────────────────────────────
const initBasicForm = () => {
  const u = authStore.userInfo || {}
  const vals = {
    username: u.username || '',
    nickname: u.nickname || '',
    email:    u.email    || '',
    phone:    u.phone    || ''
  }
  Object.assign(basicForm, vals)
  basicFormSnapshot = { ...vals }
}

const cancelEditBasic = () => {
  Object.assign(basicForm, basicFormSnapshot)
  editingBasic.value = false
}

const fetchProfile = async () => {
  const res = await getMyProfile()
  const d = res.data || {}
  profileForm.realName  = d.realName  || ''
  profileForm.skillList = d.skills    || []
  profileForm.totalHours= d.totalHours|| 0
}

const fetchStats = async () => {
  const res = await getMyRegistrations({ page: 1, size: 200 })
  const rows = res.data?.rows || []
  regRows.value  = rows
  stats.total  = rows.length
  stats.active = rows.filter(r => r.status === 0).length
}

// ── 保存基本信息 ─────────────────────────────────────────────────────
const saveBasic = async () => {
  saving.value = true
  try {
    await userUpdateInfo({
      nickname: basicForm.nickname,
      email:    basicForm.email || null,
      phone:    basicForm.phone || null
    })
    authStore.updateUserInfo({ ...authStore.userInfo, ...basicForm })
    basicFormSnapshot = { ...basicForm }
    ElMessage.success('信息保存成功')
    editingBasic.value = false
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    saving.value = false
  }
}

// ── 档案操作 ─────────────────────────────────────────────────────────
const removeSkill = (i) => profileForm.skillList.splice(i, 1)
const startAddSkill = async () => {
  addingSkill.value = true
  await nextTick()
  skillInputRef.value?.focus()
}
const confirmSkill = () => {
  const s = newSkill.value.trim()
  if (s && !profileForm.skillList.includes(s)) profileForm.skillList.push(s)
  newSkill.value   = ''
  addingSkill.value = false
}
const saveProfile = async () => {
  savingProfile.value = true
  try {
    await updateProfile({
      realName: profileForm.realName || null,
      skills:   profileForm.skillList.join(',') || null
    })
    ElMessage.success('档案保存成功')
    editingProfile.value = false
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    savingProfile.value = false
  }
}

// ── 修改密码 ─────────────────────────────────────────────────────────
const changePassword = async () => {
  await pwdFormRef.value.validate()
  changingPwd.value = true
  try {
    await changePwd(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    pwdDialogVisible.value = false
    authStore.logout()
  } catch (e) {
    // request.js 已弹出错误
  } finally {
    changingPwd.value = false
  }
}

onMounted(() => {
  initBasicForm()
  fetchProfile()
  fetchStats()
  fetchCredit()
  checkMyStatus()
})
</script>

<style scoped>
.profile-card { border-radius: 10px; }
.organizer-apply-card {
  border: 1px solid #faecd8;
  background: linear-gradient(135deg, #fffbe6 0%, #fff8d6 100%);
}

.card-title {
  font-size: 15px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ── 头像悬浮遮罩 ── */
.avatar-wrapper {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  cursor: pointer;
  margin-right: 24px;
  flex-shrink: 0;
}
.avatar-mask {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  opacity: 0;
  transition: opacity 0.2s;
}
.avatar-wrapper:hover .avatar-mask { opacity: 1; }
.avatar-spin {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: rgba(0,0,0,0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-info-box {
  display: flex;
  align-items: flex-start;
}

/* ── 数据统计卡片 ── */
.stat-box {
  text-align: center;
  padding: 16px;
  border-radius: 8px;
}
.stat-box.blue   { background: #ecf5ff; }
.stat-box.green  { background: #f0f9eb; }
.stat-box.orange { background: #fdf6ec; }
.stat-box.purple { background: #f5f0ff; }
.stat-box.gold   { background: #fffbe6; }

.stat-num {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
}
.stat-box.green  .stat-num { color: #67c23a; }
.stat-box.orange .stat-num { color: #e6a23c; }
.stat-box.purple .stat-num { color: #9b59b6; }
.stat-box.gold   .stat-num { color: #e6a23c; }

.stat-label { font-size: 13px; color: #606266; margin-top: 4px; }

.clickable {
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}

/* ── 头像勋章徽章 ── */
.avatar-medal-badge {
  position: absolute;
  bottom: -2px;
  right: -2px;
  font-size: 20px;
  line-height: 1;
  background: #fff;
  border-radius: 50%;
  padding: 1px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.2);
  pointer-events: none;
}

/* ── 等级面板 ── */
.level-panel {
  background: linear-gradient(135deg, #fffbe6 0%, #fff8d6 100%);
  border: 1px solid #ffe58f;
  border-radius: 10px;
  padding: 14px 16px 12px;
}
.level-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.level-icon-wrap {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  background: rgba(255,255,255,0.7);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.level-icon      { font-size: 26px; line-height: 1; }
.level-icon-empty { font-size: 22px; color: #c0c4cc; }
.level-title-wrap { flex: 1; }
.level-title  { font-size: 15px; font-weight: bold; }
.level-score  { font-size: 22px; font-weight: bold; color: #e6a23c; margin-top: 2px; }
.level-progress-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.level-progress-label {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.level-hint {
  font-size: 13px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.level-hint b { color: #303133; }
.level-hint-action {
  color: #409eff;
  cursor: pointer;
  margin-left: 4px;
}
</style>
