import request from '@/utils/request'

// KNN 志愿者匹配（管理员）
export function knnMatch(data) {
  return request({ url: '/ai/knn/match', method: 'post', data })
}

// KNN 活动推荐（志愿者，旧版）
export function recommendActivities(topK = 5) {
  return request({ url: '/ai/knn/recommend', method: 'get', params: { topK } })
}

// NL2SQL 自然语言查询
export function nl2sqlQuery(data) {
  return request({ url: '/ai/nl2sql/query', method: 'post', data })
}

// 志愿者流失预警
export function predictChurnRisk() {
  return request({ url: '/ai/risk/predict', method: 'get' })
}

// 管理员为指定志愿者推荐活动（流失预警联动）
export function recommendForUser(userId, topK = 5) {
  return request.get(`/ai/knn/recommend-for/${userId}`, { params: { topK } })
}

// 双阶段复合推荐引擎（Transformer向量召回 + DeepSeek Agent精排）
export function hybridRecommend(userId, activityId) {
  return request({ url: '/ai/knn/hybrid-recommend', method: 'get', params: { activityId } })
}

// Feed 流个性化推荐（行为向量 + 画像向量融合 + DeepSeek 推荐语）
// statusFilter: undefined=全部, 0=未开始, 1=报名中
export function feedRecommend(page = 1, pageSize = 6, statusFilter = undefined) {
  const params = { page, pageSize }
  if (statusFilter !== undefined && statusFilter !== null) params.statusFilter = statusFilter
  return request({ url: '/ai/knn/feed', method: 'get', params })
}
