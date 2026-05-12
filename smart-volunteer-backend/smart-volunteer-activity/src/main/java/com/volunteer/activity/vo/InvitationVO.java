package com.volunteer.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InvitationVO {
    private Long invitationId;
    private Long activityId;
    private String title;
    private String description;
    private String requiredSkills;
    private Integer totalQuota;
    private Integer joinedQuota;
    private Integer remainQuota;
    private Integer status;
    private Integer isRead;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime inviteTime;
}
