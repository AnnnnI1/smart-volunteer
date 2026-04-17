package com.volunteer.ai.vo;

import lombok.Data;
import java.util.List;

/**
 * 活动推荐结果 VO（志愿者用）
 */
@Data
public class ActivityRecommendVO {
    private Long   activityId;
    private String title;
    private String requiredSkills;
    private Integer status;
    private Integer totalQuota;
    private Integer joinedQuota;
    private Integer remainQuota;
    private String  startTime;
    private Double  similarity;
    private Integer rank;
    private List<String> matchedSkills;
}
