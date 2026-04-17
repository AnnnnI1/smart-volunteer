package com.volunteer.ai.service;

import com.volunteer.common.entity.ResponseResult;

import java.util.List;

public interface KnnService {

    /**
     * 根据活动所需技能，KNN 匹配最合适的志愿者（管理员用）
     */
    ResponseResult matchVolunteers(List<String> requiredSkills, int topK);

    /**
     * 根据志愿者自身技能，KNN 推荐最匹配的活动（志愿者用）
     */
    ResponseResult recommendActivities(Long userId, int topK);

    /**
     * 双阶段复合推荐引擎（Transformer 向量召回 + DeepSeek Agent 精排）
     */
    ResponseResult hybridRecommend(Long userId, Long activityId);

    /**
     * Feed 流个性化推荐（行为隐式反馈 + Transformer 向量融合 + DeepSeek 推荐语）
     *
     * @param userId   当前志愿者 ID
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数
     * @param statusFilter 活动状态筛选：null=全部, 0=未开始, 1=报名中
     */
    ResponseResult feedRecommend(Long userId, int page, int pageSize, Integer statusFilter);
}
