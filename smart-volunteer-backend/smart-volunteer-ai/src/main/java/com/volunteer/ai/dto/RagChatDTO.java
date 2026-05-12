package com.volunteer.ai.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG 智能问答请求：给定问题与对话历史，返回知识库引用回答
 */
@Data
public class RagChatDTO {

    /** 用户问题 */
    private String question;

    /**
     * 对话历史（最多保留最近 6 轮）
     * 每条格式：{role: "user"|"assistant", content: "..."}
     */
    private List<Map<String, String>> history;
}
