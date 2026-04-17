import request from '@/utils/request'

// 获取我的档案
export function getMyProfile() {
  return request({ url: '/user/profile', method: 'get' })
}

// 更新档案
export function updateProfile(data) {
  return request({ url: '/user/profile', method: 'put', data })
}

// 查询指定用户档案（管理员用）
export function getByUserId(userId) {
  return request({ url: `/user/profile/${userId}`, method: 'get' })
}
