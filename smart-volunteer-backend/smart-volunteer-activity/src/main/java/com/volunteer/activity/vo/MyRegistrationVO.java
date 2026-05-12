package com.volunteer.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MyRegistrationVO {
    private Long id;
    private Long userId;
    private Long activityId;
    private Integer status;
    private LocalDateTime createTime;
    /** 活动标题（JOIN vol_activity 得到） */
    private String activityTitle;
    /** 个人专属签到码（用于现场扫码签到） */
    private String checkinCode;
}
