package com.volunteer.ai.controller;

import com.volunteer.ai.dto.RagChatDTO;
import com.volunteer.ai.dto.RagDiagnoseDTO;
import com.volunteer.ai.dto.RagGenerateDTO;
import com.volunteer.ai.service.RagService;
import com.volunteer.common.entity.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * RAG 活动发布智能助手 Controller
 *
 * 接口：
 *   POST /ai/rag/chat          - RAG 智能问答（组织者/管理员）
 *   POST /ai/rag/diagnose      - RAG 活动内容诊断（组织者/管理员）
 *   POST /ai/rag/generate      - RAG 活动草稿生成（组织者/管理员）
 *   POST /ai/rag/rebuild-index - 重建知识库索引（管理员）
 */
@RestController
@RequestMapping("/ai/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    /**
     * RAG 智能问答
     *
     * @param dto 包含 question 和 history
     * @return AI 回答内容及引用来源
     */
    @PostMapping("/chat")
    public ResponseResult chat(@RequestBody RagChatDTO dto) {
        return ragService.chat(dto);
    }

    /**
     * RAG 活动内容诊断
     *
     * @param dto 活动表单字段
     * @return 结构化风险报告（评分、风险项、建议）
     */
    @PostMapping("/diagnose")
    public ResponseResult diagnose(@RequestBody RagDiagnoseDTO dto) {
        return ragService.diagnose(dto);
    }

    /**
     * RAG 活动草稿生成
     *
     * @param dto 包含 intent 和 activityType
     * @return 生成的标题、描述、技能要求、安全注意事项
     */
    @PostMapping("/generate")
    public ResponseResult generate(@RequestBody RagGenerateDTO dto) {
        return ragService.generate(dto);
    }

    /**
     * 重建 RAG 知识库索引（管理员专用）
     *
     * @return 索引构建结果
     */
    @PostMapping("/rebuild-index")
    public ResponseResult rebuildIndex() {
        return ragService.rebuildIndex();
    }
}
