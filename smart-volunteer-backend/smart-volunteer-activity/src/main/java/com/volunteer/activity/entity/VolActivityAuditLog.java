package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_activity_audit_log")
public class VolActivityAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动ID */
    private Long activityId;

    /** AI审核完整结果(JSON格式) */
    private String auditResult;

    /** 检测到的风险标签(逗号分隔) */
    private String riskTags;

    /** 是否通过: 1=通过, 0=不通过 */
    private Integer passed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
