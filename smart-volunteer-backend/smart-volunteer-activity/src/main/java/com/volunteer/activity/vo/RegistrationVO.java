package com.volunteer.activity.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RegistrationVO {
    private Long id;
    private Long userId;
    private Long activityId;
    private Integer status;
    private LocalDateTime createTime;
    /** 个人专属签到码 */
    private String checkinCode;
    // 关联用户信息
    private String username;
    private String nickname;
    private String phone;

    public String getStatusDesc() {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "已报名";
            case 1 -> "已取消";
            case 2 -> "已签到";
            case 4 -> "已缺席";
            default -> "未知";
        };
    }
}
