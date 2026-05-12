package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_report_appeal")
public class VolReportAppeal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reportId;

    private Long appellantId;

    private String appealReason;

    private String adminResponse;

    private Integer decision;

    private Long decidedBy;

    private LocalDateTime decidedAt;

    private LocalDateTime createdAt;
}
