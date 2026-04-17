package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.volunteer.activity.entity.VolRegistration;
import com.volunteer.common.entity.ResponseResult;

public interface RegistrationService extends IService<VolRegistration> {

    /** 报名接口（高并发核心） */
    ResponseResult register(Long userId, Long activityId);

    /** 取消报名 */
    ResponseResult cancel(Long userId, Long activityId);

    /** 查询用户的报名记录 */
    ResponseResult myRegistrations(Long userId, Integer page, Integer size);

    /** 管理员生成/刷新签到码（活动 status=2 时可用） */
    ResponseResult generateCheckinCode(Long userId, Long activityId);

    /**
     * 志愿者现场签到（使用个人专属签到码精确匹配，status=0 → 2）
     * 新增一人一码机制：通过 checkinCode 直接反查报名记录
     */
    ResponseResult checkinByCode(String checkinCode);

    /**
     * 志愿者现场签到（旧接口，保留兼容：通过活动ID + 签到码）
     * @deprecated 推荐使用 checkinByCode
     */
    ResponseResult checkin(Long userId, Long activityId, String code);

    /** 组织者/管理员查看活动报名名单（含用户信息） */
    ResponseResult getActivityRegistrations(Long activityId, String userId, String userRole);

    /** 组织者/管理员手动签到指定用户（status=0 → 2） */
    ResponseResult manualCheckin(Long activityId, Long targetUserId, String userId, String userRole);
}
