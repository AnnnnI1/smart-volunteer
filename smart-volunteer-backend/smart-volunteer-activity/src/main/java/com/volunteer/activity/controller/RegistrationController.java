package com.volunteer.activity.controller;

import com.volunteer.activity.service.RegistrationService;
import com.volunteer.common.entity.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import com.volunteer.common.enums.AppHttpCodeEnum;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/activity")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    /** 志愿者自助报名（userId 由 Gateway 解析 JWT 注入） */
    @PostMapping("/registration/{activityId}")
    public ResponseResult register(@RequestHeader("X-User-Id") String userId,
                                   @PathVariable Long activityId) {
        return registrationService.register(Long.parseLong(userId), activityId);
    }

    /**
     * 管理员代报名：KNN 匹配后直接为指定志愿者报名
     */
    @PostMapping("/registration/admin/register")
    public ResponseResult adminRegister(@RequestBody Map<String, Long> body) {
        Long userId     = body.get("userId");
        Long activityId = body.get("activityId");
        return registrationService.register(userId, activityId);
    }

    /** 取消报名 */
    @DeleteMapping("/registration/{activityId}")
    public ResponseResult cancel(@RequestHeader("X-User-Id") String userId,
                                 @PathVariable Long activityId) {
        return registrationService.cancel(Long.parseLong(userId), activityId);
    }

    /** 我的报名记录 */
    @GetMapping("/registration/my")
    public ResponseResult myRegistrations(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return registrationService.myRegistrations(Long.parseLong(userId), page, size);
    }

    /** 管理员/组织者生成/刷新签到码（活动进行中时） */
    @PostMapping("/{id}/checkin-code")
    public ResponseResult generateCheckinCode(@RequestHeader("X-User-Id") String userId,
                                              @RequestHeader("X-User-Role") String userRole,
                                              @PathVariable Long id) {
        int role = Integer.parseInt(userRole);
        if (role != 0 && role != 2) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return registrationService.generateCheckinCode(Long.parseLong(userId), id);
    }

    /** 志愿者现场签到（新版一人一码：通过个人专属签到码精确匹配） */
    @PostMapping("/checkin")
    public ResponseResult checkin(@RequestBody Map<String, String> body) {
        String checkinCode = body.get("checkinCode");
        if (checkinCode == null || checkinCode.isBlank()) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_ERROR, "请输入签到码");
        }
        return registrationService.checkinByCode(checkinCode.toUpperCase());
    }

    /** 志愿者现场签到（旧版兼容：需传入活动ID+签到码） */
    @PostMapping("/{id}/checkin")
    public ResponseResult checkin(@RequestHeader("X-User-Id") String userId,
                                  @PathVariable Long id,
                                  @RequestBody Map<String, String> body) {
        return registrationService.checkin(Long.parseLong(userId), id, body.get("code"));
    }

    /** 组织者/管理员查看活动报名名单 */
    @GetMapping("/{id}/registrations")
    public ResponseResult getRegistrations(@RequestHeader("X-User-Id") String userId,
                                           @RequestHeader("X-User-Role") String userRole,
                                           @PathVariable Long id) {
        return registrationService.getActivityRegistrations(id, userId, userRole);
    }

    /** 组织者/管理员手动签到指定用户 */
    @PostMapping("/{id}/manual-checkin/{targetUserId}")
    public ResponseResult manualCheckin(@RequestHeader("X-User-Id") String userId,
                                        @RequestHeader("X-User-Role") String userRole,
                                        @PathVariable Long id,
                                        @PathVariable Long targetUserId) {
        return registrationService.manualCheckin(id, targetUserId, userId, userRole);
    }
}
