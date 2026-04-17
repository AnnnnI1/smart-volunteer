package com.volunteer.activity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityVO {

    private Long id;
    private String title;
    private String description;
    private String requiredSkills;
    private Integer totalQuota;
    private Integer joinedQuota;
    /** 剩余名额（Redis 实时值） */
    private Integer remainQuota;
    private Integer status;
    /** 审核状态：0=待审核 1=通过 2=驳回 */
    private Integer auditStatus;
    private Long organizerId;
    private String organizerName;
    private String statusDesc;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;

    public String getStatusDesc() {
        if (status == null) return "";
        return switch (status) {
            case 0 -> "未开始";
            case 1 -> "报名中";
            case 2 -> "进行中";
            case 3 -> "已结束";
            default -> "未知";
        };
    }
}
