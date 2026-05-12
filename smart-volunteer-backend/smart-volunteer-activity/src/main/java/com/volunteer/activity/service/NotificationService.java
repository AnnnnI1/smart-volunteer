package com.volunteer.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.volunteer.common.entity.ResponseResult;

public interface NotificationService {

    /** 发送通知 */
    void send(Long userId, String title, String content, String type, Long relatedId);

    /** 查询用户未读通知数 */
    ResponseResult getUnreadCount(Long userId);

    /** 查询用户通知列表 */
    ResponseResult listNotifications(Long userId, Integer page, Integer size);

    /** 标记指定通知为已读 */
    ResponseResult markAsRead(Long notificationId, Long userId);

    /** 标记所有通知为已读 */
    ResponseResult markAllAsRead(Long userId);
}
