package com.volunteer.credit.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_credit_balance")
public class VolCreditBalance {

    @TableId
    private Long userId;

    private Integer balance;

    private LocalDateTime updateTime;
}
