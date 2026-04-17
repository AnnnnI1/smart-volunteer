package com.volunteer.ai.vo;

import lombok.Data;
import java.util.List;

/** 流失预警单条结果 */
@Data
public class RiskVO {
    private Long userId;
    private String nickname;
    private Integer signupCount;
    private Integer cancelCount;
    private Double cancelRate;
    private Integer totalHours;
    private Integer inactiveDays;
    /** 风险评分 0~1 */
    private Double riskScore;
    /** 高 / 中 / 低 */
    private String riskLevel;
    /** Element Plus tag type */
    private String riskColor;
    /** 风险因素说明列表 */
    private List<String> riskFactors;
}
