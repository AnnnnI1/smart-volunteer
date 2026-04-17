-- ============================================================
-- 智能志愿者管理系统 - 数据库结构扩展脚本 (MySQL 8.0+)
-- 执行时间：2026-04-10
-- ============================================================

-- 1. 【模块1】users 表新增字段
ALTER TABLE users
ADD COLUMN apply_reason VARCHAR(500) DEFAULT NULL COMMENT '申请成为组织者的理由',
ADD COLUMN audit_status TINYINT DEFAULT 0 COMMENT '审核状态: 0=待审, 1=通过, 2=驳回',
ADD COLUMN ai_audit_report TEXT DEFAULT NULL COMMENT 'AI尽调报告(JSON格式)';

-- 2. 【模块2】vol_activity 表新增字段
ALTER TABLE vol_activity
ADD COLUMN audit_status TINYINT DEFAULT 0 COMMENT 'AI风控审核状态: 0=待审, 1=通过, 2=驳回';

-- 3. 【模块3】vol_registration 表新增字段
ALTER TABLE vol_registration
ADD COLUMN checkin_code VARCHAR(8) DEFAULT NULL COMMENT '个人专属签到码(SHA-256前8位)';

-- 为 checkin_code 添加索引
ALTER TABLE vol_registration
ADD INDEX idx_checkin_code (checkin_code);

-- 4. 【模块2】新建活动风控日志表
CREATE TABLE IF NOT EXISTS vol_activity_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    audit_result JSON NOT NULL COMMENT 'AI审核完整结果',
    risk_tags VARCHAR(500) DEFAULT NULL COMMENT '检测到的风险标签(逗号分隔)',
    passed TINYINT NOT NULL COMMENT '是否通过: 1=通过, 0=不通过',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_id (activity_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动AI风控审核日志';
