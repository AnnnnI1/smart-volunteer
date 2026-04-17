package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.volunteer.activity.dto.AddActivityDTO;
import com.volunteer.activity.dto.UpdateActivityDTO;
import com.volunteer.activity.entity.VolActivity;
import com.volunteer.activity.entity.VolActivityAuditLog;
import com.volunteer.common.entity.ResponseResult;

import java.util.List;

public interface ActivityService extends IService<VolActivity> {

    ResponseResult addActivity(AddActivityDTO dto, String userId, String userRole);

    ResponseResult updateActivity(Long id, UpdateActivityDTO dto, String userId, String userRole);

    ResponseResult deleteActivity(Long id, String userId, String userRole);

    ResponseResult getActivityById(Long id);

    ResponseResult listActivities(Integer status, boolean includeAll, Integer page, Integer size);

    /** 修改活动状态（含发布时初始化 Redis 名额） */
    ResponseResult updateStatus(Long id, Integer status, String userId, String userRole);

    /** 组织者查看自己的活动 */
    ResponseResult getMyActivities(String userId, Integer page, Integer size);

    /** 查询活动AI风控日志列表（管理员） */
    ResponseResult listAuditLogs(Integer passed, Integer page, Integer size);

    /** 管理员人工审核通过（仅更新 auditStatus=1，不改 status） */
    ResponseResult approveActivity(Long id, String userId, String userRole);
}
