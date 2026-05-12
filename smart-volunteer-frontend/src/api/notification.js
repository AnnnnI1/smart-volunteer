import request from '@/utils/request'

// 查询未读通知数量
export function getUnreadNotificationCount() {
  return request({ url: '/notification/unread-count', method: 'get' })
}

// 查询通知列表
export function getNotificationList(params) {
  return request({ url: '/notification/list', method: 'get', params })
}

// 标记单条已读
export function markNotificationRead(id) {
  return request({ url: `/notification/${id}/read`, method: 'put' })
}

// 全部标记已读
export function markAllNotificationsRead() {
  return request({ url: '/notification/read-all', method: 'put' })
}
