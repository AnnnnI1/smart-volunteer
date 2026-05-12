package com.volunteer.activity.service;

import com.volunteer.common.entity.ResponseResult;

public interface InvitationService {

    /** 管理员向指定志愿者发送活动邀请 */
    ResponseResult sendInvitation(Long activityId, Long userId);

    /** 获取当前用户的邀请列表（含活动详情） */
    ResponseResult getMyInvitations(Long userId);

    /** 将当前用户所有未读邀请标记为已读，并返回最新未读数 */
    ResponseResult markAllRead(Long userId);

    /** 获取未读邀请数量（用于首次加载徽章） */
    ResponseResult getUnreadCount(Long userId);

    /** 志愿者删除自己的某条邀请 */
    ResponseResult deleteInvitation(Long invitationId, Long userId);
}
