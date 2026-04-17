package com.volunteer.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * KNN 匹配请求：给定活动所需技能，找出最合适的 K 名志愿者
 */
@Data
public class KnnMatchDTO {

    /** 活动所需技能标签列表，如 ["医疗", "急救"] */
    @NotEmpty(message = "技能标签不能为空")
    private List<String> requiredSkills;

    /** 返回结果数量，默认 5 */
    @NotNull
    private Integer topK = 5;
}
