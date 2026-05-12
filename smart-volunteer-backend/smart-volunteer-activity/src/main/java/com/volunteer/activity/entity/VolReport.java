package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_report")
public class VolReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reportType;

    private String categoryCode;

    private Long reporterId;

    private Long reportedUserId;

    private Long activityId;

    private String description;

    private String evidenceUrls;

    private Integer status;

    private Integer priority;

    private Long assignedTo;

    private String aiAnalysis;

    private String adminDecision;

    private String adminPenalty;

    private Integer reportSource;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;
}
