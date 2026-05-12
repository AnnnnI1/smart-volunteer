package com.volunteer.ai.dto;

import lombok.Data;

/**
 * RAG 活动草稿生成请求：根据一句话意图生成完整活动草稿
 */
@Data
public class RagGenerateDTO {

    /** 组织者描述的活动意图，如"我想组织一次社区义诊" */
    private String intent;

    /**
     * 活动类型（可选），如"社区服务"、"教育帮扶"、"环保"等
     * 传入后 AI 生成时参考对应模板风格
     */
    private String activityType;
}
