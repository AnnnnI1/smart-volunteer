<template>
  <div class="feed-card" @click="emit('click')">
    <!-- 头部：标题 + 状态 -->
    <div class="fc-header">
      <div class="fc-title">{{ item.title }}</div>
      <el-tag
        :type="statusMap[item.status]?.type || 'info'"
        size="small"
        effect="light"
        style="flex-shrink:0"
      >{{ statusMap[item.status]?.label || '-' }}</el-tag>
    </div>

    <!-- 技能标签 -->
    <div v-if="item.required_skills" class="fc-skills">
      <el-tag
        v-for="s in item.required_skills.split(',').filter(Boolean).slice(0, 4)"
        :key="s"
        size="small"
        type="info"
        effect="plain"
        style="margin:2px"
      >{{ s }}</el-tag>
    </div>

    <!-- 元信息行 -->
    <div class="fc-meta">
      <span><el-icon><Calendar /></el-icon> {{ fmtDate(item.start_time) }}</span>
      <span>
        <el-icon><User /></el-icon>
        剩余
        <b :style="{ color: item.remain_quota <= 3 ? '#f56c6c' : '#303133' }">
          {{ item.remain_quota }}
        </b>
        名额
      </span>
    </div>

    <!-- 匹配度进度条 -->
    <div class="fc-score-row">
      <span class="fc-score-label">AI匹配</span>
      <el-progress
        :percentage="Math.min(100, Math.round((item.feed_score || 0) * 100))"
        :color="scoreColor(item.feed_score || 0)"
        :stroke-width="6"
        :show-text="false"
        style="flex:1"
      />
      <span class="fc-score-num" :style="{ color: scoreColor(item.feed_score || 0) }">
        {{ ((item.feed_score || 0) * 100).toFixed(0) }}%
      </span>
    </div>

    <!-- 打字机推荐语 -->
    <div class="fc-reason" :class="{ 'fc-reason-fallback': item.fallback_reason }">
      <el-icon style="font-size:12px;flex-shrink:0;margin-top:2px"><ChatLineSquare /></el-icon>
      <span class="fc-reason-text">
        {{ displayed }}<span class="typewriter-cursor">|</span>
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { Calendar, User, ChatLineSquare } from '@element-plus/icons-vue'

const props = defineProps({
  item: { type: Object, required: true }
})
const emit = defineEmits(['click'])

const statusMap = {
  0: { label: '未开始', type: 'info' },
  1: { label: '报名中', type: 'success' },
  2: { label: '进行中', type: 'primary' },
  3: { label: '已结束', type: 'danger' }
}
const fmtDate   = (dt) => dt ? String(dt).substring(0, 10) : '-'
const scoreColor = (s) => s >= 0.6 ? '#67c23a' : s >= 0.35 ? '#e6a23c' : '#909399'

// ── 打字机效果 ────────────────────────────────────────────────────
const displayed = ref('')
let timer = null

const startTypewriter = (text) => {
  displayed.value = ''
  clearInterval(timer)
  if (!text) return
  let idx = 0
  timer = setInterval(() => {
    displayed.value += text[idx]
    idx++
    if (idx >= text.length) clearInterval(timer)
  }, 38)
}

onMounted(() => startTypewriter(props.item?.ai_reason || ''))
watch(() => props.item?.ai_reason, (v) => startTypewriter(v || ''))
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.feed-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.15s;
}
.feed-card:hover {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.12);
  border-color: #409eff;
  transform: translateY(-2px);
}

.fc-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.fc-title {
  font-size: 14px;
  font-weight: bold;
  color: #303133;
  line-height: 1.5;
  flex: 1;
}

.fc-skills { margin-bottom: 8px; }

.fc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.fc-meta .el-icon {
  vertical-align: -2px;
  margin-right: 2px;
}

.fc-score-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}
.fc-score-label {
  font-size: 11px;
  color: #909399;
  white-space: nowrap;
}
.fc-score-num {
  font-size: 12px;
  font-weight: bold;
  white-space: nowrap;
}

.fc-reason {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  background: #f0f7ff;
  border-left: 3px solid #409eff;
  border-radius: 0 6px 6px 0;
  padding: 6px 10px;
  font-size: 12px;
  color: #303133;
  line-height: 1.6;
  min-height: 36px;
}
.fc-reason-fallback {
  background: #f5f5f5;
  border-color: #dcdfe6;
  color: #909399;
}
.fc-reason-text { flex: 1; }

.typewriter-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: #409eff;
  font-weight: bold;
  margin-left: 1px;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0; }
}
</style>
