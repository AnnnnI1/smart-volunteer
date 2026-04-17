package com.volunteer.activity.controller;

import com.volunteer.activity.service.InvitationService;
import com.volunteer.common.entity.ResponseResult;
import com.volunteer.common.enums.AppHttpCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activity")
public class InvitationController {

    @Autowired
    private InvitationService invitationService;

    /** 管理员向指定志愿者发送邀请 */
    @PostMapping("/{activityId}/invite/{userId}")
    public ResponseResult sendInvitation(@RequestHeader("X-User-Role") String userRole,
                                         @PathVariable Long activityId,
                                         @PathVariable Long userId) {
        if (Integer.parseInt(userRole) != 0) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NO_OPERATOR_AUTH);
        }
        return invitationService.sendInvitation(activityId, userId);
    }

    /** 志愿者/组织者获取自己的邀请列表 */
    @GetMapping("/invitations/mine")
    public ResponseResult getMyInvitations(@RequestHeader("X-User-Id") String userId) {
        return invitationService.getMyInvitations(Long.parseLong(userId));
    }

    /** 打开弹窗时标记全部已读，返回 0 */
    @PutMapping("/invitations/read")
    public ResponseResult markAllRead(@RequestHeader("X-User-Id") String userId) {
        return invitationService.markAllRead(Long.parseLong(userId));
    }

    /** 页面加载时获取未读数量（轻量接口，只返回数字） */
    @GetMapping("/invitations/unread-count")
    public ResponseResult getUnreadCount(@RequestHeader("X-User-Id") String userId) {
        return invitationService.getUnreadCount(Long.parseLong(userId));
    }

    /** 志愿者删除自己的某条邀请 */
    @DeleteMapping("/invitations/delete/{invitationId}")
    public ResponseResult deleteInvitation(@RequestHeader("X-User-Id") String userId,
                                           @PathVariable Long invitationId) {
        return invitationService.deleteInvitation(invitationId, Long.parseLong(userId));
    }
}
