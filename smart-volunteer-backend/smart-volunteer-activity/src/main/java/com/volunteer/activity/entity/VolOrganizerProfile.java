package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("vol_organizer_profile")
public class VolOrganizerProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long organizerId;

    private Integer totalSubmissions;

    private Integer totalRejected;

    private Integer totalReports;

    private BigDecimal avgParticipantCancelRate;

    private Integer bypassAttempts;

    private LocalDateTime forceManualReviewUntil;

    private Integer riskLevel;

    private LocalDateTime lastUpdated;
}
