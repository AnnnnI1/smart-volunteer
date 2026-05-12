package com.volunteer.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.volunteer.ai.dto.RagChatDTO;
import com.volunteer.ai.dto.RagDiagnoseDTO;
import com.volunteer.ai.dto.RagGenerateDTO;
import com.volunteer.ai.service.RagService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG 活动发布智能助手服务实现
 *
 * 架构说明：
 *   Java 层负责数据封装与请求转发 → HTTP 调用 Python AI 节点
 *   Python 层负责 RAG 检索（ChromaDB + sentence-transformers）+ DeepSeek 生成
 *   实现活动发布"发布前合规辅导 + 发布中诊断 + 发布后审核"的三阶段闭环
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    @Value("${python.ai.url}")
    private String pythonAiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ResponseResult chat(RagChatDTO dto) {
        if (dto.getQuestion() == null || dto.getQuestion().isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "问题不能为空");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("question", dto.getQuestion());
        body.put("history", dto.getHistory() != null ? dto.getHistory() : java.util.Collections.emptyList());

        try {
            String resp = webClient()
                    .post()
                    .uri("/rag/chat")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            if (resp == null || resp.isBlank()) {
                return ResponseResult.okResult(Map.of(
                        "answer", "抱歉，AI 助手暂时无法响应，请稍后重试。",
                        "sources", java.util.Collections.emptyList()
                ));
            }

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            if (data == null) {
                return ResponseResult.okResult(Map.of(
                        "answer", "抱歉，AI 助手暂时无法响应，请稍后重试。",
                        "sources", java.util.Collections.emptyList()
                ));
            }
            return ResponseResult.okResult(objectMapper.convertValue(data, Object.class));

        } catch (Exception e) {
            log.error("RAG 智能问答调用失败: {}", e.getMessage());
            return ResponseResult.okResult(Map.of(
                    "answer", "抱歉，AI 助手暂时不可用，请稍后重试或联系平台管理员确认服务状态。",
                    "sources", java.util.Collections.emptyList()
            ));
        }
    }

    @Override
    public ResponseResult diagnose(RagDiagnoseDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "活动标题不能为空");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("title", dto.getTitle());
        body.put("description", dto.getDescription() != null ? dto.getDescription() : "");
        body.put("required_skills", dto.getRequiredSkills() != null ? dto.getRequiredSkills() : "");
        body.put("total_quota", dto.getTotalQuota() != null ? dto.getTotalQuota() : 0);
        body.put("start_time", dto.getStartTime() != null ? dto.getStartTime() : "");
        body.put("end_time", dto.getEndTime() != null ? dto.getEndTime() : "");
        body.put("location", dto.getLocation() != null ? dto.getLocation() : "");
        body.put("outdoor", dto.getOutdoor() != null ? dto.getOutdoor() : false);
        body.put("involves_minors", dto.getInvolvesMinors() != null ? dto.getInvolvesMinors() : false);
        body.put("requires_professional_skill", dto.getRequiresProfessionalSkill() != null ? dto.getRequiresProfessionalSkill() : false);
        body.put("risk_note", dto.getRiskNote() != null ? dto.getRiskNote() : "");

        try {
            String resp = webClient()
                    .post()
                    .uri("/rag/diagnose")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            return ResponseResult.okResult(objectMapper.convertValue(data, Object.class));

        } catch (Exception e) {
            log.error("RAG 活动诊断调用失败: {}", e.getMessage());
            return ResponseResult.errorResult(500, "AI 诊断服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public ResponseResult generate(RagGenerateDTO dto) {
        if (dto.getIntent() == null || dto.getIntent().isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "活动意图不能为空");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("intent", dto.getIntent());
        body.put("activity_type", dto.getActivityType() != null ? dto.getActivityType() : "");

        try {
            String resp = webClient()
                    .post()
                    .uri("/rag/generate")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            return ResponseResult.okResult(objectMapper.convertValue(data, Object.class));

        } catch (Exception e) {
            log.error("RAG 草稿生成调用失败: {}", e.getMessage());
            return ResponseResult.errorResult(500, "AI 生成服务暂时不可用，请稍后重试");
        }
    }

    @Override
    public ResponseResult rebuildIndex() {
        try {
            String resp = webClient()
                    .post()
                    .uri("/rag/rebuild-index")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            JsonNode root = objectMapper.readTree(resp);
            JsonNode data = root.get("data");
            return ResponseResult.okResult(objectMapper.convertValue(data, Object.class));

        } catch (Exception e) {
            log.error("RAG 索引重建调用失败: {}", e.getMessage());
            return ResponseResult.errorResult(500, "索引重建失败，请检查 Python AI 服务是否正常");
        }
    }

    /** 复用 KnnServiceImpl 中的 WebClient 构建方式 */
    private WebClient webClient() {
        return WebClient.builder()
                .baseUrl(pythonAiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
