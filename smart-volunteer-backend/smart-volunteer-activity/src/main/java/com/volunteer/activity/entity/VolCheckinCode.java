package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_checkin_code")
public class VolCheckinCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String code;

    private LocalDateTime createTime;

    /** 1=有效, 0=已失效 */
    private Integer isActive;

    /** 签到码过期时间（60秒后过期） */
    private LocalDateTime expireAt;
}
