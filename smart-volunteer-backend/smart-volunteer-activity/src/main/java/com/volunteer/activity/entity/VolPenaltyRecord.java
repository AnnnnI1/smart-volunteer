package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_penalty_record")
public class VolPenaltyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long reportId;

    private String penaltyType;

    private String penaltyValue;

    private Long operatorId;

    private String aiSuggested;

    private String reason;

    private Integer isReversed;

    private Long reversedBy;

    private LocalDateTime reversedAt;

    private LocalDateTime createdAt;
}
