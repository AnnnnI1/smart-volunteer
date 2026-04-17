<template>
  <div class="feed-page">

    <!-- 顶栏 -->
    <div class="feed-header">
      <div class="feed-title">
        <el-icon style="color:#409EFF;font-size:18px"><MagicStick /></el-icon>
        <span>为我推荐</span>
        <el-tag
          v-if="vectorMode"
          size="small"
          :type="vectorMode === 'behavior+profile' ? 'success' : vectorMode === 'profile_only' ? 'primary' : 'info'"
          effect="light"
          style="margin-left:8px"
        >{{ vectorModeLabel }}</el-tag>
      </div>
      <div style="display:flex;gap:8px;align-items:center">
        <el-radio-group v-model="statusFilter" size="small" @change="onFilterChange">
          <el-radio-button :value="null">全部</el-radio-button>
          <el-radio-button :value="1">报名中</el-radio-button>
          <el-radio-button :value="0">未开始</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="refreshing" circle @click="onRefresh">
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 瀑布流主体 -->
    <div
      v-infinite-scroll="loadMore"
      :infinite-scroll-disabled="scrollDisabled"
      :infinite-scroll-distance="100"
      class="feed-scroll-container"
    >
      <div class="waterfall">
        <!-- 左列 -->
        <div class="waterfall-col">
          <FeedCard
            v-for="item in leftCol"
            :key="item.id"
            :item="item"
            @click="goDetail(item.id)"
          />
        </div>
        <!-- 右列 -->
        <div class="waterfall-col">
          <FeedCard
            v-for="item in rightCol"
            :key="item.id"
            :item="item"
            @click="goDetail(item.id)"
          />
        </div>
      </div>

      <!-- 底部状态 -->
      <div class="feed-footer">
        <div v-if="loading" class="feed-loading">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>AI 正在为你生成专属推荐…</span>
        </div>
        <div v-else-if="!hasMore && feedItems.length > 0" class="feed-end">
          已为你呈现全部 {{ total }} 个推荐活动 ✨
        </div>
        <el-empty
          v-else-if="!loading && feedItems.length === 0"
          description="暂无推荐活动，试试切换筛选条件"
          :image-size="80"
        />
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Refresh, Loading } from '@element-plus/icons-vue'
import { feedRecommend } from '@/api/ai'
import FeedCard from './FeedCard.vue'

const router = useRouter()

// ── 响应式状态 ─────────────────────────────────────────────────────
const feedItems    = ref([])
const page         = ref(1)
const pageSize     = 6
const total        = ref(0)
const hasMore      = ref(true)
const loading      = ref(false)
const refreshing   = ref(false)
const vectorMode   = ref('')
const statusFilter = ref(null)

const preloadBuffer  = ref([])
const preloadHasMore = ref(true)
const preloading     = ref(false)

// ── 瀑布流分列 ─────────────────────────────────────────────────────
const leftCol  = computed(() => feedItems.value.filter((_, i) => i % 2 === 0))
const rightCol = computed(() => feedItems.value.filter((_, i) => i % 2 === 1))

// ── 向量模式标签 ───────────────────────────────────────────────────
const vectorModeLabel = computed(() => {
  const m = vectorMode.value
  if (m === 'behavior+profile') return '行为+画像向量'
  if (m === 'profile_only')     return '画像向量'
  if (m === 'fallback')         return '热门推荐'
  return ''
})

const scrollDisabled = computed(() => loading.value || !hasMore.value)

// ── 拉取一页 ───────────────────────────────────────────────────────
const fetchPage = async (p) => {
  const res = await feedRecommend(p, pageSize, statusFilter.value)
  return res.data
}

// ── 初始加载 ───────────────────────────────────────────────────────
const init = async () => {
  loading.value = true
  feedItems.value = []
  page.value = 1
  hasMore.value = true
  preloadBuffer.value = []
  preloadHasMore.value = true
  try {
    const data = await fetchPage(1)
    feedItems.value  = data.items || []
    total.value      = data.total || 0
    hasMore.value    = data.has_more ?? false
    vectorMode.value = data.vector_mode || ''
    page.value = 2
    if (hasMore.value) silentPreload(2)
  } catch {
    // request.js 已弹错误
  } finally {
    loading.value = false
  }
}

// ── 静默预加载 ─────────────────────────────────────────────────────
const silentPreload = async (p) => {
  if (preloading.value) return
  preloading.value = true
  try {
    const data = await fetchPage(p)
    preloadBuffer.value  = data.items || []
    preloadHasMore.value = data.has_more ?? false
    total.value = data.total || 0
  } catch {
    preloadBuffer.value = []
  } finally {
    preloading.value = false
  }
}

// ── 触底加载 ───────────────────────────────────────────────────────
const loadMore = async () => {
  if (loading.value || !hasMore.value) return

  // 优先消费预加载缓冲
  if (preloadBuffer.value.length > 0) {
    feedItems.value.push(...preloadBuffer.value)
    hasMore.value = preloadHasMore.value
    page.value++
    preloadBuffer.value = []
    if (hasMore.value) silentPreload(page.value)
    return
  }

  // 缓冲空时同步加载
  loading.value = true
  try {
    const data = await fetchPage(page.value)
    feedItems.value.push(...(data.items || []))
    hasMore.value = data.has_more ?? false
    total.value   = data.total || 0
    page.value++
    if (hasMore.value) silentPreload(page.value)
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

// ── 筛选 / 刷新 / 跳转 ────────────────────────────────────────────
const onFilterChange = () => init()
const onRefresh = async () => { refreshing.value = true; await init(); refreshing.value = false }
const goDetail  = (id) => router.push(`/homepage/activity/${id}`)

onMounted(init)
</script>

<style scoped>
.feed-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 14px 0;
  flex-shrink: 0;
}
.feed-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 17px;
  font-weight: bold;
  color: #303133;
}

.feed-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 20px;
}

.waterfall {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.waterfall-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.feed-footer {
  padding: 20px 0 10px;
  text-align: center;
}
.feed-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: #409eff;
}
.feed-end {
  font-size: 13px;
  color: #c0c4cc;
}
</style>
