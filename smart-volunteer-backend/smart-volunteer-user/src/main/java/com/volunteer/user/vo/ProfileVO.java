package com.volunteer.user.vo;

import lombok.Data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Data
public class ProfileVO {

    private Long userId;

    private String realName;

    /** 技能标签列表（前端直接使用） */
    private List<String> skills;

    /** 累计服务时长（小时） */
    private Integer totalHours;

    /** 将逗号分隔的技能字符串拆分为列表 */
    public void setSkillsFromString(String skillsStr) {
        if (skillsStr == null || skillsStr.isBlank()) {
            this.skills = Collections.emptyList();
        } else {
            this.skills = Arrays.asList(skillsStr.split(","));
        }
    }
}
