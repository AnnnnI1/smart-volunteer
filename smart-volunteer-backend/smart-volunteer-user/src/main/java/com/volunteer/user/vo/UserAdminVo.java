package com.volunteer.user.vo;

import lombok.Data;

import java.util.Date;

@Data
public class UserAdminVo {
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer role;
    private Integer status;
    private Integer applyOrganizer;
    private Date createdAt;

    // 组织者申请相关
    private String applyReason;
    private Integer auditStatus;
    private String aiAuditReport;

    // 用户统计（管理员用户列表详情展示）
    private Integer creditBalance;
    private Integer signupCount;
    private Integer cancelCount;
    private Integer totalHours;
}
