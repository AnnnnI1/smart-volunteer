package com.volunteer.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("vol_profile")
public class VolProfile {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String realName;

    /** 技能标签，逗号分隔，如 "医疗,教育,翻译" */
    private String skills;

    private Integer totalHours;
}
