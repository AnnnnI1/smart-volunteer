package com.volunteer.ai.service;

import com.volunteer.ai.dto.RagChatDTO;
import com.volunteer.ai.dto.RagDiagnoseDTO;
import com.volunteer.ai.dto.RagGenerateDTO;
import com.volunteer.common.entity.ResponseResult;

/**
 * RAG 活动发布智能助手服务接口
 *
 * 转发请求到 Python AI 微服务（RAG 知识库检索 + DeepSeek 生成）
 */
public interface RagService {

    /**
     * RAG 智能问答：检索知识库后由 DeepSeek 生成回答
     *
     * @param dto 包含 question 和 history
     * @return AI 回答内容及引用来源
     */
    ResponseResult chat(RagChatDTO dto);

    /**
     * RAG 活动内容诊断：结合表单字段和规则知识库，输出结构化风险报告
     *
     * @param dto 活动表单字段
     * @return 诊断结果（风险评分、风险项列表、修改建议）
     */
    ResponseResult diagnose(RagDiagnoseDTO dto);

    /**
     * RAG 活动草稿生成：根据一句话意图生成完整活动发布草稿
     *
     * @param dto 包含 intent 和 activityType
     * @return 生成的标题、描述、技能要求、安全注意事项
     */
    ResponseResult generate(RagGenerateDTO dto);

    /**
     * 重建 RAG 知识库索引（管理员用）
     *
     * 读取 knowledge/ 目录下的 Markdown 文件，重新向量化并写入 ChromaDB
     *
     * @return 索引构建结果
     */
    ResponseResult rebuildIndex();
}
