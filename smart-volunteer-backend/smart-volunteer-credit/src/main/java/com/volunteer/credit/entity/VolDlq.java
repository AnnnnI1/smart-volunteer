package com.volunteer.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_dlq")
public class VolDlq {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String msgId;
    private String topic;
    private String body;
    private String errorMsg;
    private Integer reconsumeTimes;
    /** 0=待处理 1=已手动处理 */
    private Integer status;
    private LocalDateTime createTime;
}
