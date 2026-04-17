package com.volunteer.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水明细
 * change_type: 1=报名奖励 2=完成奖励 3=取消扣分 4=管理员调整
 */
@Data
@TableName("vol_credit_record")
public class VolCreditRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Integer changeType;

    private Integer points;

    private Integer balanceAfter;

    private String remark;

    private LocalDateTime createTime;
}
