package com.volunteer.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("vol_notification")
public class VolNotification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通知接收人ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /**
     * 通知类型：
     * REPORT_SUBMIT       - 举报已提交通知（仅通知被举报人）
     * REPORT_ACCEPTED    - 举报已被受理
     * REPORT_RESOLVED     - 举报已处理结案
     * APPEAL_SUBMITTED    - 申诉已提交（通知管理员）
     * APPEAL_RESULT      - 申诉结果通知（通知申诉人）
     */
    private String type;

    /** 关联业务ID（举报ID/申诉ID等） */
    private Long relatedId;

    /** 是否已读：0-未读 1-已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}
