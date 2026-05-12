package com.volunteer.ai.dto;

import lombok.Data;

/**
 * RAG 活动内容诊断请求：给定活动表单字段，返回结构化风险报告
 */
@Data
public class RagDiagnoseDTO {

    /** 活动标题 */
    private String title;

    /** 活动描述 */
    private String description;

    /** 所需技能（逗号分隔） */
    private String requiredSkills;

    /** 总名额 */
    private Integer totalQuota;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 活动地点 */
    private String location;

    /** 是否户外活动 */
    private Boolean outdoor = false;

    /** 是否涉及未成年人 */
    private Boolean involvesMinors = false;

    /** 是否需要专业技能 */
    private Boolean requiresProfessionalSkill = false;

    /** 风险备注 */
    private String riskNote;
}
