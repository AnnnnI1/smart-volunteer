package com.volunteer.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 志愿者档案扩展表
 * skills 字段存储逗号分隔的技能标签，如 "医疗,教育,翻译"
 * 供 KNN 匹配算法向量化使用
 */
@Data
@TableName("vol_profile")
public class VolProfile {

    /** 关联 users.id，既是 PK 也是 FK */
    @TableId(type = IdType.INPUT)
    private Long userId;

    private String realName;

    /** 技能标签（逗号分隔），如 "医疗,教育,翻译" */
    private String skills;

    /** 累计服务时长（小时） */
    private Integer totalHours;
}
