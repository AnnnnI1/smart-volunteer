<template>
  <el-dialog
    v-model="visible"
    :title="isProcessed ? '申诉详情' : '处理申诉'"
    width="600px"
    destroy-on-close
  >
    <el-descriptions :column="2" border>
      <el-descriptions-item label="申诉ID">{{ appeal.id }}</el-descriptions-item>
      <el-descriptions-item label="关联举报">
        <el-button type="primary" link size="small" @click="$emit('open-report', appeal.reportId)">#{{ appeal.reportId }}</el-button>
      </el-descriptions-item>
      <el-descriptions-item label="申诉人">
        <el-button link type="primary" @click="openUserDetail(appeal.appellantId)">
          {{ appeal.appellantNickname || appeal.appellantUsername || ('ID: ' + appeal.appellantId) }}
        </el-button>
      </el-descriptions-item>
      <el-descriptions-item label="举报类型">
        <el-tag :type="appeal.reportType === 'AR' ? 'danger' : 'warning'" size="small">
          {{ appeal.reportType === 'AR' ? '活动举报' : '志愿者违规' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="申诉理由" :span="2">{{ appeal.appealReason }}</el-descriptions-item>
      <el-descriptions-item label="提交时间">{{ fmt(appeal.createdAt) }}</el-descriptions-item>
      <el-descriptions-item label="申诉结果">
        <el-tag v-if="appeal.decision == null" type="warning" size="small">待处理</el-tag>
        <el-tag v-else :type="decisionTagType(appeal.decision)" size="small">{{ appeal.decisionDesc }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="appeal.decision != null" label="管理员回复" :span="2">{{ appeal.adminResponse || '-' }}</el-descriptions-item>
      <el-descriptions-item v-if="appeal.decidedAt" label="处理时间" :span="2">{{ fmt(appeal.decidedAt) }}</el-descriptions-item>
    </el-descriptions>

    <!-- 处理表单（仅未处理时显示） -->
    <el-form v-if="!isProcessed" style="margin-top:20px">
      <el-form-item label="处理结果" required>
        <el-radio-group v-model="form.decision">
          <el-radio :value="0">维持原判</el-radio>
          <el-radio :value="1">撤销惩罚</el-radio>
          <el-radio :value="2">重新处理</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="回复说明">
        <el-input v-model="form.adminResponse" type="textarea" :rows="3" placeholder="请输入管理员回复说明" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="!isProcessed" type="primary" :loading="processing" @click="handleSubmit">
        确认提交
      </el-button>
    </template>
  </el-dialog>

  <UserDetailDialog v-model="userDetailVisible" :user-id="currentUserId" />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { processAppeal } from '@/api/report'
import UserDetailDialog from './UserDetailDialog.vue'

const props = defineProps({
  modelValue: Boolean,
  appealId: { type: Number, default: null },
  appealData: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'refresh', 'open-report'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const appeal = ref(null)
const processing = ref(false)
const form = ref({ decision: 0, adminResponse: '' })
const userDetailVisible = ref(false)
const currentUserId = ref(null)

const isProcessed = computed(() => appeal.value && appeal.value.decision != null)

const openUserDetail = (userId) => {
  if (!userId) return
  currentUserId.value = userId
  userDetailVisible.value = true
}

const decisionTagType = (d) => ({ 0: 'info', 1: 'success', 2: 'danger' }[d] || 'info')

const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').substring(0, 16) : '-'

watch(() => props.modelValue, async (val) => {
  if (val) {
    if (props.appealData) {
      appeal.value = { ...props.appealData }
    } else {
      // 通过详情接口获取
      const { default: request } = await import('@/utils/request')
      try {
        const res = await request({ url: `/report/admin/appeals?page=1&size=50` })
        const found = res.data?.rows?.find(a => a.id === props.appealId)
        if (found) appeal.value = found
      } catch (e) {
        ElMessage.error('加载申诉详情失败')
      }
    }
    form.value = { decision: 0, adminResponse: '' }
  }
})

const handleSubmit = async () => {
  if (!form.value.adminResponse?.trim()) {
    ElMessage.warning('请输入回复说明')
    return
  }
  processing.value = true
  try {
    await processAppeal(appeal.value.id, {
      decision: form.value.decision,
      adminResponse: form.value.adminResponse
    })
    ElMessage.success('处理成功')
    visible.value = false
    emit('refresh')
  } catch (e) {
    ElMessage.error('处理失败')
  } finally {
    processing.value = false
  }
}
</script>
