package com.volunteer.ai.controller;

import com.volunteer.ai.dto.KnnMatchDTO;
import com.volunteer.ai.service.KnnService;
import com.volunteer.common.entity.ResponseResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai/knn")
public class KnnController {

    @Autowired
    private KnnService knnService;

    /**
     * KNN 志愿者匹配（管理员用）
     */
    @PostMapping("/match")
    public ResponseResult match(@RequestBody @Valid KnnMatchDTO dto) {
        return knnService.matchVolunteers(dto.getRequiredSkills(), dto.getTopK());
    }

    /**
     * 活动智能推荐（志愿者用，旧版 KNN）
     */
    @GetMapping("/recommend")
    public ResponseResult recommend(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "5") int topK) {
        return knnService.recommendActivities(userId, topK);
    }

    /**
     * 管理员为指定志愿者推荐活动（流失预警联动）
     */
    @GetMapping("/recommend-for/{userId}")
    public ResponseResult recommendForUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "1") int role,
            @RequestParam(defaultValue = "5") int topK) {
        if (role != 0) {
            return ResponseResult.errorResult(403, "仅管理员可用");
        }
        return knnService.recommendActivities(userId, topK);
    }

    /**
     * 双阶段复合推荐引擎（管理员活动诊断用）
     */
    @GetMapping("/hybrid-recommend")
    public ResponseResult hybridRecommend(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long activityId) {
        return knnService.hybridRecommend(userId, activityId);
    }

    /**
     * Feed 流个性化推荐（志愿者/组织者「为我推荐」页面）
     *
     * 融合行为隐式反馈向量 + 技能画像向量，全局余弦相似度排序后分页返回，
     * 每批次由 DeepSeek 生成专属推荐语（≤30字）。
     *
     * @param page         页码，从 1 开始（触底加载时自增）
     * @param pageSize     每页条数，默认 6
     * @param statusFilter 活动状态筛选：不传=全部(0+1), 0=未开始, 1=报名中
     */
    @GetMapping("/feed")
    public ResponseResult feed(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int pageSize,
            @RequestParam(required = false) Integer statusFilter) {
        return knnService.feedRecommend(userId, page, pageSize, statusFilter);
    }
}
