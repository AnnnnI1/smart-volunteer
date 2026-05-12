package com.volunteer.activity.controller;

import com.volunteer.activity.service.NotificationService;
import com.volunteer.common.entity.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /** 查询当前用户的未读通知数 */
    @GetMapping("/unread-count")
    public ResponseResult unreadCount(@RequestHeader("X-User-Id") String userId) {
        return notificationService.getUnreadCount(Long.parseLong(userId));
    }

    /** 查询当前用户的通知列表 */
    @GetMapping("/list")
    public ResponseResult list(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return notificationService.listNotifications(Long.parseLong(userId), page, size);
    }

    /** 标记单条通知为已读 */
    @PutMapping("/{id}/read")
    public ResponseResult markRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable Long id) {
        return notificationService.markAsRead(id, Long.parseLong(userId));
    }

    /** 标记所有通知为已读 */
    @PutMapping("/read-all")
    public ResponseResult markAllRead(@RequestHeader("X-User-Id") String userId) {
        return notificationService.markAllAsRead(Long.parseLong(userId));
    }
}
