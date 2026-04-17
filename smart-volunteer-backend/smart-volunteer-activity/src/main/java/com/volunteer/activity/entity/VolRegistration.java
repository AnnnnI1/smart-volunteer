package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_registration")
public class VolRegistration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    /**
     * 状态: 0-报名成功 1-已取消 2-已完成
     */
    private Integer status;

    /** 个人专属签到码(SHA-256前8位) */
    private String checkinCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
