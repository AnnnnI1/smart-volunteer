import request from '@/utils/request'

// 提交举报（志愿者/组织者/管理员）
export function submitReport(data) {
  return request({ url: '/report/submit', method: 'post', data })
}

// 用户查询自己提交的举报列表
export function listMyReports(params) {
  return request({ url: '/report/my/reports', method: 'get', params })
}

// 用户查询举报自己的列表
export function listMyReceivedReports(params) {
  return request({ url: '/report/my/received', method: 'get', params })
}

// 用户查看自己的信用分
export function getUserCreditScore(userId) {
  return request({ url: `/report/credit/${userId}`, method: 'get' })
}

// 用户查询自己的申诉列表
export function listMyAppeals(params) {
  return request({ url: '/report/my/appeals', method: 'get', params })
}

// 管理员：举报列表
export function listReports(params) {
  return request({ url: '/report/admin/list', method: 'get', params })
}

// 管理员：举报详情
export function getReportDetail(reportId) {
  return request({ url: `/report/admin/${reportId}`, method: 'get' })
}

// 管理员：受理举报
export function acceptReport(reportId) {
  return request({ url: `/report/admin/${reportId}/accept`, method: 'post' })
}

// 管理员：处理举报
export function processReport(reportId, data) {
  return request({ url: `/report/admin/${reportId}/process`, method: 'post', data })
}

// 管理员：批量 AI 分析
export function batchAnalyzeReports(reportIds) {
  return request({ url: '/report/admin/batch-analyze', method: 'post', data: { reportIds } })
}

// 管理员：举报统计数据
export function getReportStatistics() {
  return request({ url: '/report/admin/statistics', method: 'get' })
}

// 管理员：惩罚记录列表
export function listPenaltyRecords(params) {
  return request({ url: '/report/admin/penalties', method: 'get', params })
}

// 提交申诉（被举报人）
export function submitAppeal(reportId, data) {
  return request({ url: `/report/appeal/${reportId}`, method: 'post', data })
}

// 管理员：申诉列表
export function listAppeals(params) {
  return request({ url: '/report/admin/appeals', method: 'get', params })
}

// 管理员：处理申诉
export function processAppeal(appealId, data) {
  return request({ url: `/report/admin/appeals/${appealId}/process`, method: 'post', data })
}

// 管理员：信用分列表
export function listCreditScores(params) {
  return request({ url: '/report/admin/credits', method: 'get', params })
}

// 管理员：强制解封
export function forceUnban(userId) {
  return request({ url: `/report/admin/unban/${userId}`, method: 'post' })
}

// 管理员：黑名单
export function listBlacklist(params) {
  return request({ url: '/report/admin/blacklist', method: 'get', params })
}

// 管理员：撤销惩罚记录
export function reversePenalty(penaltyId) {
  return request({ url: `/report/admin/penalty/${penaltyId}/reverse`, method: 'post' })
}
