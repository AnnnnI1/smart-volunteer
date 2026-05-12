package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_local_message")
public class VolLocalMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一消息ID，幂等键 */
    private String messageId;

    /** 业务类型，如 ACTIVITY_REGISTER */
    private String businessType;

    /** JSON 业务数据 */
    private String content;

    /**
     * 状态: 0-待处理 1-处理成功 2-处理失败
     */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
