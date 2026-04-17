-- ============================================================
-- 智能志愿者管理系统 - 数据库结构扩展脚本
-- 执行时间：2026-04-10
-- 说明：为以下四个模块执行 DDL 变更：
--   模块1: 组织者入驻 AI 尽调
--   模块2: 活动发布 AI 风控
--   模块3: 防作弊一人一码签到
--   模块4: AI 推荐引擎（无需数据库变更）
-- ============================================================

-- 1. 【模块1】users 表新增字段
-- 新增字段：apply_reason (申请理由), audit_status (审核状态), ai_audit_report (AI尽调报告)
ALTER TABLE users
ADD COLUMN IF NOT EXISTS apply_reason VARCHAR(500) DEFAULT NULL COMMENT '申请成为组织者的理由',
ADD COLUMN IF NOT EXISTS audit_status TINYINT DEFAULT 0 COMMENT '审核状态: 0=待审, 1=通过, 2=驳回',
ADD COLUMN IF NOT EXISTS ai_audit_report TEXT DEFAULT NULL COMMENT 'AI尽调报告(JSON格式)';

-- 2. 【模块2】vol_activity 表新增字段
-- 新增字段：audit_status (AI风控审核状态)
ALTER TABLE vol_activity
ADD COLUMN IF NOT EXISTS audit_status TINYINT DEFAULT 0 COMMENT 'AI风控审核状态: 0=待审, 1=通过, 2=驳回';

-- 3. 【模块3】vol_registration 表新增字段
-- 新增字段：checkin_code (个人专属签到码)
ALTER TABLE vol_registration
ADD COLUMN IF NOT EXISTS checkin_code VARCHAR(8) DEFAULT NULL COMMENT '个人专属签到码(SHA-256前8位)';

-- 为 checkin_code 添加索引，提升签到查询性能
ALTER TABLE vol_registration
ADD INDEX IF NOT EXISTS idx_checkin_code (checkin_code);

-- 4. 【模块2】新建活动风控日志表
CREATE TABLE IF NOT EXISTS vol_activity_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    activity_id BIGINT NOT NULL COMMENT '活动ID',
    audit_result JSON NOT NULL COMMENT 'AI审核完整结果',
    risk_tags VARCHAR(500) DEFAULT NULL COMMENT '检测到的风险标签(逗号分隔)',
    passed TINYINT NOT NULL COMMENT '是否通过: 1=通过, 0=不通过',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_activity_id (activity_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动AI风控审核日志';

-- ============================================================
-- 验证脚本：查询新增的字段和表
-- ============================================================
SELECT '=== 验证 users 表新增字段 ===' AS '';
DESCRIBE users;

SELECT '=== 验证 vol_activity 表新增字段 ===' AS '';
DESCRIBE vol_activity;

SELECT '=== 验证 vol_registration 表新增字段 ===' AS '';
DESCRIBE vol_registration;

SELECT '=== 验证 vol_activity_audit_log 表 ===' AS '';
DESCRIBE vol_activity_audit_log;

SELECT '=== 当前数据库版本验证 ===' AS '';
SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema = DATABASE();
