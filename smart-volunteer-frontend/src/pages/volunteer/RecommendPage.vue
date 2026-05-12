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
import { ref, computed, onMounted, nextTick } from 'vue'
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
const currentSeed    = ref(0)
const initializing   = ref(false)  // init 执行期间禁用 infinite-scroll，防止误触发

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

const scrollDisabled = computed(() => loading.value || !hasMore.value || initializing.value)

// ── 拉取一页 ───────────────────────────────────────────────────────
const fetchPage = async (p, seed) => {
  const res = await feedRecommend(p, pageSize, statusFilter.value, seed)
  return res.data
}

// ── 初始加载 ───────────────────────────────────────────────────────
const init = async (withSeed = false) => {
  initializing.value = true
  loading.value = true
  feedItems.value = []
  page.value = 1
  hasMore.value = true
  // 清空预取缓冲，防止刷新期间旧缓冲数据被误消费
  preloadBuffer.value = []
  preloadHasMore.value = true
  // 立即更新 seed，确保第1页和所有并行预取使用同一个新种子
  currentSeed.value = withSeed ? Date.now() % 100000 : 0
  try {
    const data = await fetchPage(1, currentSeed.value)
    feedItems.value  = data.items || []
    total.value      = data.total || 0
    hasMore.value    = data.has_more ?? false
    vectorMode.value = data.vector_mode || ''
    page.value = 2
    if (hasMore.value) silentPreload(2, currentSeed.value)
  } catch {
    // request.js 已弹错误
  } finally {
    loading.value = false
    initializing.value = false
  }
  // infinite-scroll 在刷新/筛选后不会主动重新检查容器是否需要填充
  // 手动触发一次，loadMore 内部有完整的 guard，不会重复加载
  if (hasMore.value) {
    await nextTick()
    loadMore()
  }
}

// ── 静默预加载 ─────────────────────────────────────────────────────
const silentPreload = async (p, seed) => {
  if (preloading.value) return
  preloading.value = true
  try {
    const data = await fetchPage(p, seed)
    // 新鲜度校验：只有当预取页码与待消费页码匹配时才接受缓冲
    // 刷新期间 page.value 可能已重置，此时丢弃旧缓冲
    if (p === page.value) {
      preloadBuffer.value  = data.items || []
      preloadHasMore.value = data.has_more ?? false
      total.value = data.total || 0
    }
  } catch {
    if (p === page.value) {
      preloadBuffer.value = []
    }
  } finally {
    preloading.value = false
  }
}

// ── 触底加载 ───────────────────────────────────────────────────────
const loadMore = async () => {
  if (loading.value || !hasMore.value || initializing.value) return

  // 优先消费预加载缓冲（仅当缓冲新鲜时）
  if (preloadBuffer.value.length > 0) {
    feedItems.value.push(...preloadBuffer.value)
    hasMore.value = preloadHasMore.value
    page.value++
    preloadBuffer.value = []
    if (hasMore.value) silentPreload(page.value, currentSeed.value)
    return
  }

  // 缓冲空时同步加载
  loading.value = true
  try {
    const data = await fetchPage(page.value, currentSeed.value)
    feedItems.value.push(...(data.items || []))
    hasMore.value = data.has_more ?? false
    total.value   = data.total || 0
    page.value++
    if (hasMore.value) silentPreload(page.value, currentSeed.value)
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

// ── 筛选 / 刷新 / 跳转 ────────────────────────────────────────────
const onFilterChange = () => init(false)
const onRefresh = async () => { refreshing.value = true; await init(true); refreshing.value = false }
const goDetail  = (id) => router.push(`/homepage/activity/${id}`)

onMounted(() => init(false))
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
