import request from '@/utils/request'

// 查询当前用户积分余额
export function getCreditBalance() {
  return request({ url: '/credit/balance', method: 'get' })
}

// 查询积分流水（分页）
export function getCreditRecords(params) {
  return request({ url: '/credit/records', method: 'get', params })
}

// 管理员手动调整积分
export function adminAdjustCredit(data) {
  return request({ url: '/credit/admin/adjust', method: 'post', data })
}
