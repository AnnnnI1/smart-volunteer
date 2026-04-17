package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_activity")
public class VolActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    /** 活动所需技能（逗号分隔），供 KNN 推荐使用，如 "医疗,急救" */
    private String requiredSkills;

    private Integer totalQuota;

    private Integer joinedQuota;

    /**
     * 状态: 0-未开始 1-报名中 2-进行中 3-已结束
     */
    private Integer status;

    private Long organizerId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** AI风控审核状态: 0=待审, 1=通过, 2=驳回 */
    private Integer auditStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
