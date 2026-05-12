import request from '@/utils/request'

/**
 * RAG 活动发布智能助手 API
 *
 * 封装 Java AI Service 的 /ai/rag/* 端点
 */

/**
 * RAG 智能问答
 * @param {Object} data - { question: string, history?: Array<{role, content}> }
 * @returns {Promise} - { code, data: { answer, sources } }
 */
export function ragChat(data) {
  return request({
    url: '/ai/rag/chat',
    method: 'post',
    data
  })
}

/**
 * RAG 活动内容合规诊断
 * @param {Object} data - 活动表单字段
 * @returns {Promise} - { code, data: { overallScore, riskLevel, canSubmit, summary, risks, suggestions } }
 */
export function ragDiagnose(data) {
  return request({
    url: '/ai/rag/diagnose',
    method: 'post',
    data
  })
}

/**
 * RAG 活动草稿生成
 * @param {Object} data - { intent: string, activityType?: string }
 * @returns {Promise} - { code, data: { title, description, requiredSkills, safetyNote, tips } }
 */
export function ragGenerate(data) {
  return request({
    url: '/ai/rag/generate',
    method: 'post',
    data
  })
}

/**
 * 重建 RAG 知识库索引（管理员用）
 * @returns {Promise}
 */
export function rebuildRagIndex() {
  return request({
    url: '/ai/rag/rebuild-index',
    method: 'post'
  })
}
