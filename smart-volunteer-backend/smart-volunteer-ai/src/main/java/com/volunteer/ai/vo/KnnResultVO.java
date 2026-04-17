package com.volunteer.ai.vo;

import lombok.Data;
import java.util.List;

/**
 * KNN 匹配单条结果（Python 增强版）
 */
@Data
public class KnnResultVO {
    private Long userId;
    private String realName;
    private String skills;
    private Integer totalHours;
    /** 积分余额 */
    private Integer creditBalance;
    /** 余弦相似度（TF-IDF 加权，Python 计算）*/
    private Double similarity;
    /** 时长加成分（最多 +0.10）*/
    private Double hoursScore;
    /** 积分信誉加成分（最多 +0.15）*/
    private Double creditScore;
    /** 出勤率加成分（最多 +0.20）*/
    private Double attendanceScore;
    /** 出勤率 0.0~1.0 */
    private Double attendanceRate;
    /** 综合评分（相似度 + 时长 + 积分 + 出勤）*/
    private Double finalScore;
    /** 与活动需求重叠的技能 */
    private List<String> matchedSkills;
    /** 排名 */
    private Integer rank;
}
