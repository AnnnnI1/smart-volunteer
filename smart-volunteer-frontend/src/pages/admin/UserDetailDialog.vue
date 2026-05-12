<template>
  <el-dialog
    v-model="visible"
    :title="detail ? ('用户详情 — ' + (detail.nickname || detail.username || '')) : '用户详情'"
    width="560px"
    destroy-on-close
  >
    <div v-if="loading" v-loading="true" style="height:200px" />
    <template v-else-if="detail">
      <div class="detail-section-title">基本信息</div>
      <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
        <el-descriptions-item label="用户ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detail.nickname || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="roleType(detail.role)" size="small">{{ roleLabel(detail.role) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="账号状态">
          <el-tag :type="detail.status === 1 ? 'success' : 'danger'" size="small">
            {{ detail.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.phone || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <div class="detail-section-title">活动统计</div>
      <div style="display:flex;gap:12px;flex-wrap:wrap;margin-bottom:16px">
        <div class="detail-stat-card">
          <div class="detail-stat-num" style="color:#e6a23c">{{ detail.creditBalance ?? 0 }}</div>
          <div class="detail-stat-label">积分余额</div>
        </div>
        <div class="detail-stat-card">
          <div class="detail-stat-num" style="font-weight:bold"
            :style="{ color: (detail.creditScore ?? 70) < 40 ? '#f56c6c' : (detail.creditScore ?? 70) < 60 ? '#e6a23c' : '#67c23a' }">
            {{ detail.creditScore ?? 70 }}
          </div>
          <div class="detail-stat-label">信用分</div>
        </div>
        <div class="detail-stat-card">
          <div class="detail-stat-num" style="color:#409EFF">{{ detail.signupCount ?? 0 }}</div>
          <div class="detail-stat-label">报名次数</div>
        </div>
        <div class="detail-stat-card">
          <div class="detail-stat-num" style="color:#f56c6c">{{ detail.cancelCount ?? 0 }}</div>
          <div class="detail-stat-label">取消次数</div>
        </div>
        <div class="detail-stat-card">
          <div class="detail-stat-num" style="color:#67c23a">{{ detail.totalHours ?? 0 }}</div>
          <div class="detail-stat-label">服务时长(h)</div>
        </div>
        <div class="detail-stat-card" v-if="(detail.signupCount ?? 0) > 0">
          <div class="detail-stat-num" style="color:#909399">
            {{ Math.round(((detail.signupCount ?? 0) - (detail.cancelCount ?? 0)) / (detail.signupCount ?? 1) * 100) }}%
          </div>
          <div class="detail-stat-label">出勤率</div>
        </div>
      </div>
    </template>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { adminGetUser } from '@/api/user'

const props = defineProps({
  modelValue: Boolean,
  userId: { type: Number, default: null }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const detail = ref(null)

watch(visible, async (v) => {
  if (v && props.userId) {
    loading.value = true
    detail.value = null
    try {
      const res = await adminGetUser(props.userId)
      detail.value = res.data
    } catch (e) {
      detail.value = null
    } finally {
      loading.value = false
    }
  }
})

const roleType = (r) => ({ 0: 'danger', 1: 'primary', 2: 'warning' }[r] || 'info')
const roleLabel = (r) => ({ 0: '管理员', 1: '志愿者', 2: '组织者' }[r] || '未知')
const fmt = (d) => d ? new Date(d).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-').substring(0, 16) : '-'
</script>

<style scoped>
.detail-section-title {
  font-size: 13px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 8px;
  padding-left: 8px;
  border-left: 3px solid #409EFF;
}
.detail-stat-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  text-align: center;
  min-width: 80px;
}
.detail-stat-num {
  font-size: 20px;
  font-weight: bold;
  line-height: 1;
}
.detail-stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
