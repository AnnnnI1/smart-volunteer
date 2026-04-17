package com.volunteer.activity.controller;

import com.volunteer.activity.dto.AddActivityDTO;
import com.volunteer.activity.dto.UpdateActivityDTO;
import com.volunteer.activity.service.ActivityService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity")
public class ActivityController {

    @Autowired
    private ActivityService activityService;

    /** 新增活动（管理员或组织者） */
    @PostMapping
    public ResponseResult add(@RequestHeader("X-User-Id") String userId,
                              @RequestHeader("X-User-Role") String userRole,
                              @RequestBody @Valid AddActivityDTO dto) {
        return activityService.addActivity(dto, userId, userRole);
    }

    /** 编辑活动（管理员或本人组织者） */
    @PutMapping("/{id}")
    public ResponseResult update(@RequestHeader("X-User-Id") String userId,
                                 @RequestHeader("X-User-Role") String userRole,
                                 @PathVariable Long id,
                                 @RequestBody @Valid UpdateActivityDTO dto) {
        return activityService.updateActivity(id, dto, userId, userRole);
    }

    /** 删除活动（管理员或本人组织者） */
    @DeleteMapping("/{id}")
    public ResponseResult delete(@RequestHeader("X-User-Id") String userId,
                                 @RequestHeader("X-User-Role") String userRole,
                                 @PathVariable Long id) {
        return activityService.deleteActivity(id, userId, userRole);
    }

    /** 活动详情 */
    @GetMapping("/{id}")
    public ResponseResult detail(@PathVariable Long id) {
        return activityService.getActivityById(id);
    }

    /** 活动列表（可按状态筛选；管理员可传 includeAll=true 查看全量含待审核） */
    @GetMapping("/list")
    public ResponseResult list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") boolean includeAll,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return activityService.listActivities(status, includeAll, page, size);
    }

    /** 修改活动状态：0→1(开放报名) 1→2(开始) 2→3(结束) */
    @PutMapping("/{id}/status/{status}")
    public ResponseResult updateStatus(@RequestHeader("X-User-Id") String userId,
                                       @RequestHeader("X-User-Role") String userRole,
                                       @PathVariable Long id,
                                       @PathVariable Integer status) {
        return activityService.updateStatus(id, status, userId, userRole);
    }

    /** 查询活动AI风控日志列表（仅管理员） */
    @GetMapping("/audit-logs")
    public ResponseResult listAuditLogs(
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) Integer passed,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return activityService.listAuditLogs(passed, page, size);
    }

    /** 组织者查看自己的活动 */
    @GetMapping("/mine")
    public ResponseResult myActivities(@RequestHeader("X-User-Id") String userId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return activityService.getMyActivities(userId, page, size);
    }

    /** 管理员人工审核通过（仅更新 auditStatus=1，不改 status） */
    @PutMapping("/{id}/approve")
    public ResponseResult approveActivity(@RequestHeader("X-User-Id") String userId,
                                          @RequestHeader("X-User-Role") String userRole,
                                          @PathVariable Long id) {
        return activityService.approveActivity(id, userId, userRole);
    }
}
