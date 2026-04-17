package com.volunteer.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("users")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String nickname;
    private String passwordHash;
    private String email;
    private String phone;
    private String avatar;
    private Integer role;
    private Integer status;
    private Integer applyOrganizer;
    /** 申请成为组织者的理由 */
    private String applyReason;
    /** 审核状态: 0=待审, 1=通过, 2=驳回 */
    private Integer auditStatus;
    /** AI尽调报告(JSON格式) */
    private String aiAuditReport;
    private Date createdAt;
    private Date updatedAt;
    private Date lastLogin;
}
