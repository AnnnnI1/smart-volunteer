package com.volunteer.user.dto;

import lombok.Data;

/**
 * 更新志愿者档案请求体
 * userId 由 Gateway 解析 JWT 后通过 X-User-Id 传入，不在请求体中
 */
@Data
public class ProfileUpdateDTO {

    private String realName;

    /** 技能标签（逗号分隔），如 "医疗,教育,翻译" */
    private String skills;
}
