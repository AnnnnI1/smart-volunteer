package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_user_credit_score")
public class VolUserCreditScore {

    @TableId(type = IdType.ASSIGN_ID)
    private Long userId;

    private Integer creditScore;

    private Integer totalViolations;

    private Integer totalReports;

    private LocalDateTime banUntil;

    private Integer banType;

    private String banReason;

    private LocalDateTime activityLimitUntil;

    private Integer demotionCount;

    private Integer organizerLevel;

    private LocalDateTime lastViolationAt;

    private LocalDateTime lastUpdated;

    public Integer getBanStatus() {
        if (banType == null || banType == 0) return 0;
        if (banType == 2) return 1;
        if (banType == 1 && banUntil != null && banUntil.isAfter(LocalDateTime.now())) return 1;
        return 0;
    }

    public Integer getActivityLimit() {
        if (banType == null || banType == 0) return 0;
        if (banType == 2) return 0;
        return 3;
    }
}
