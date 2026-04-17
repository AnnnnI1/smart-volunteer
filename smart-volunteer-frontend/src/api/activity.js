import request from '@/utils/request'

// 活动列表
export function getActivityList(params) {
  return request({ url: '/activity/list', method: 'get', params })
}

// 活动详情
export function getActivityDetail(id) {
  return request({ url: `/activity/${id}`, method: 'get' })
}

// 新增活动
export function addActivity(data) {
  return request({ url: '/activity', method: 'post', data })
}

// 编辑活动
export function updateActivity(id, data) {
  return request({ url: `/activity/${id}`, method: 'put', data })
}

// 删除活动
export function deleteActivity(id) {
  return request({ url: `/activity/${id}`, method: 'delete' })
}

// 修改活动状态
export function updateActivityStatus(id, status) {
  return request({ url: `/activity/${id}/status/${status}`, method: 'put' })
}

// 报名
export function registerActivity(activityId) {
  return request({ url: `/activity/registration/${activityId}`, method: 'post' })
}

// 取消报名
export function cancelRegistration(activityId) {
  return request({ url: `/activity/registration/${activityId}`, method: 'delete' })
}

// 我的报名记录
export function getMyRegistrations(params) {
  return request({ url: '/activity/registration/my', method: 'get', params })
}

// 管理员代报名（KNN 匹配后直接帮志愿者报名）
export function adminRegister(userId, activityId) {
  return request({ url: '/activity/registration/admin/register', method: 'post', data: { userId, activityId } })
}

// 管理员生成/刷新签到码（活动进行中时）
export function generateCheckinCode(activityId) {
  return request({ url: `/activity/${activityId}/checkin-code`, method: 'post' })
}

// 志愿者现场签到
export function submitCheckin(activityId, code) {
  return request({ url: `/activity/${activityId}/checkin`, method: 'post', data: { code } })
}

// 组织者扫码签到（使用个人专属签到码精确匹配）
export function checkinByCode(data) {
  return request({ url: '/activity/checkin', method: 'post', data })
}

// 组织者查看自己发起的活动
export function getMyActivities(params) {
  return request({ url: '/activity/mine', method: 'get', params })
}

// 组织者/管理员查看活动报名名单
export function getActivityRegistrations(activityId) {
  return request({ url: `/activity/${activityId}/registrations`, method: 'get' })
}

// 组织者/管理员手动签到
export function manualCheckin(activityId, targetUserId) {
  return request({ url: `/activity/${activityId}/manual-checkin/${targetUserId}`, method: 'post' })
}

// ========== 邀请相关 ==========

// 管理员向志愿者发送活动邀请（不直接报名，仅发通知）
export function sendInvitation(activityId, userId) {
  return request({ url: `/activity/${activityId}/invite/${userId}`, method: 'post' })
}

// 获取我的邀请列表（含活动详情）
export function getMyInvitations() {
  return request({ url: '/activity/invitations/mine', method: 'get' })
}

// 标记所有邀请为已读
export function markInvitationsRead() {
  return request({ url: '/activity/invitations/read', method: 'put' })
}

// 获取未读邀请数量
export function getUnreadInvitationCount() {
  return request({ url: '/activity/invitations/unread-count', method: 'get' })
}

// 删除某条邀请
export function deleteInvitation(invitationId) {
  return request({ url: `/activity/invitations/delete/${invitationId}`, method: 'delete' })
}

// ========== 活动AI风控日志 ==========

// 查询活动AI风控日志（管理员）
export function getActivityAuditLogs(params) {
  return request({ url: '/activity/audit-logs', method: 'get', params })
}

// 管理员手动审核通过活动（仅更新 auditStatus=1，不改 status，不直接开放报名）
export function adminApproveActivity(id) {
  return request({ url: `/activity/${id}/approve`, method: 'put' })
}
