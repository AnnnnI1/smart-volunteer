-- Smart Volunteer Database Initialization
-- Runs automatically on first MySQL container start

USE smart_volunteer;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table: users
-- ----------------------------
CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名（唯一）',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户昵称',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MD5加密密码',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `role` tinyint(1) NOT NULL DEFAULT '1' COMMENT '0=管理员 1=志愿者 2=组织者',
  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '0=未激活 1=激活 2=禁用',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `last_login` datetime DEFAULT NULL,
  `apply_organizer` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

INSERT INTO `users` VALUES
(1,'admin','管理员','e10adc3949ba59abbe56e057f20f883e','test@example.com','13800000001',NULL,0,1,NOW(),NOW(),NULL,0),
(2,'john_doe','John','47ec2dd791e31e2ef2076caf64ed9b3d','john@example.com','13800000002',NULL,1,1,NOW(),NOW(),NULL,0),
(3,'jane_smith','Jane','47ec2dd791e31e2ef2076caf64ed9b3d','jane@example.com',NULL,NULL,1,1,NOW(),NOW(),NULL,0),
(21,'volunteer1','李志愿','e10adc3949ba59abbe56e057f20f883e',NULL,'13255663326',NULL,1,1,NOW(),NOW(),NULL,0),
(22,'volunteer2','王医生','e10adc3949ba59abbe56e057f20f883e',NULL,NULL,NULL,1,1,NOW(),NOW(),NULL,0),
(28,'orgtest1','organizer1','19fc0880ecb16a348d6d1631270c4191',NULL,NULL,NULL,2,1,NOW(),NOW(),NULL,0),
(29,'voltest1','volunteer1','47ec2dd791e31e2ef2076caf64ed9b3d',NULL,NULL,NULL,1,1,NOW(),NOW(),NULL,0);

-- ----------------------------
-- Table: vol_activity
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `title` varchar(128) NOT NULL COMMENT '活动标题',
  `description` text COMMENT '活动描述',
  `total_quota` int NOT NULL DEFAULT '0' COMMENT '总名额',
  `joined_quota` int NOT NULL DEFAULT '0' COMMENT '已报名人数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=未开始 1=报名中 2=进行中 3=已结束',
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `required_skills` varchar(500) DEFAULT '' COMMENT '所需技能',
  `organizer_id` bigint DEFAULT NULL COMMENT '发起人ID',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COMMENT='志愿活动表';

INSERT INTO `vol_activity` VALUES
(19,'社区义务教学志愿服务','为社区留守儿童提供课外辅导，帮助他们提升学习成绩与自信心。',20,5,1,'2026-04-01 09:00:00','2026-04-01 17:00:00',NOW(),NOW(),'教育,耐心',28),
(20,'城市马拉松医疗保障志愿者','为城市马拉松赛事提供医疗急救保障，确保参赛选手安全。',15,0,2,'2026-04-05 06:00:00','2026-04-05 14:00:00',NOW(),NOW(),'医疗,急救',28),
(21,'环保植树造林活动','参与城郊荒地植树造林，改善生态环境，践行绿色生活。',50,0,3,'2026-04-10 08:00:00','2026-04-10 16:00:00',NOW(),NOW(),'环保,体育',28),
(22,'老年人智能手机使用培训','帮助老年人学习智能手机基本操作，融入数字生活。',25,0,0,'2026-04-15 14:00:00','2026-04-15 17:00:00',NOW(),NOW(),'教育,耐心',28),
(23,'国际文化交流翻译志愿者','为来华留学生文化交流活动提供中英文翻译服务。',10,0,0,'2026-04-20 10:00:00','2026-04-20 18:00:00',NOW(),NOW(),'翻译,文化',28),
(24,'社区心理健康公益讲座','组织心理健康知识宣讲，为居民提供情绪疏导与心理援助。',30,0,0,'2026-04-22 14:00:00','2026-04-22 17:00:00',NOW(),NOW(),'心理,社区服务',28),
(25,'青少年法律意识普及活动','面向中学生开展法律知识宣传，增强青少年法律意识。',20,0,0,'2026-05-01 09:00:00','2026-05-01 12:00:00',NOW(),NOW(),'法律,教育',28),
(26,'图书馆整理与阅读推广','协助图书馆整理书籍，并向读者推荐优质阅读资源。',12,0,0,'2026-05-08 09:00:00','2026-05-08 17:00:00',NOW(),NOW(),'文化,社区服务',28),
(27,'残障人士出行陪伴服务','陪伴行动不便的残障人士外出就医或购物，给予生活关怀。',18,0,0,'2026-05-15 08:00:00','2026-05-15 18:00:00',NOW(),NOW(),'社区服务,耐心',28),
(28,'应急救援技能培训演练','学习火灾逃生、心肺复苏等应急救援基本技能，提升自救互救能力。',40,1,2,'2026-05-20 09:00:00','2026-05-20 16:00:00',NOW(),NOW(),'急救,医疗',28);

-- ----------------------------
-- Table: vol_checkin_code
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_checkin_code` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` bigint NOT NULL,
  `code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `is_active` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table: vol_credit_balance
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_credit_balance` (
  `user_id` bigint NOT NULL,
  `balance` int NOT NULL DEFAULT '0',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table: vol_credit_record
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_credit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `activity_id` bigint DEFAULT NULL,
  `change_type` tinyint NOT NULL,
  `points` int NOT NULL,
  `balance_after` int NOT NULL,
  `remark` varchar(128) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  UNIQUE KEY `uk_user_activity_type` (`user_id`, `activity_id`, `change_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table: vol_local_message
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_local_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `message_id` varchar(64) NOT NULL,
  `business_type` varchar(32) NOT NULL,
  `content` json NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_id` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';

-- ----------------------------
-- Table: vol_profile
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_profile` (
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `real_name` varchar(32) DEFAULT NULL COMMENT '真实姓名',
  `skills` varchar(256) DEFAULT NULL COMMENT '技能标签',
  `total_hours` int DEFAULT '0' COMMENT '总服务时长',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿者信息扩展表';

INSERT INTO `vol_profile` VALUES
(1,'张管理员','医疗,急救,翻译,心理',0),
(21,'李志愿','教育,翻译,文化',0),
(22,'王医生','医疗,急救,翻译',0);

-- ----------------------------
-- Table: vol_registration
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_registration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `activity_id` bigint NOT NULL,
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0=已报名 1=已取消 2=已签到 4=缺席',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_activity` (`user_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动报名记录表';

INSERT INTO `vol_registration` VALUES
(28,11,19,0,NOW()),
(29,2,19,0,NOW()),
(30,3,19,0,NOW()),
(31,19,19,0,NOW()),
(32,29,19,0,NOW()),
(33,21,28,2,NOW());

-- ----------------------------
-- Table: vol_dlq (死信队列补偿表)
-- ----------------------------
CREATE TABLE IF NOT EXISTS `vol_dlq` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `msg_id` varchar(64) NOT NULL COMMENT 'RocketMQ消息ID',
  `topic` varchar(64) NOT NULL COMMENT '原始topic',
  `body` text NOT NULL COMMENT '消息体JSON',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '最后一次异常信息',
  `reconsume_times` int DEFAULT 0 COMMENT '已重试次数',
  `status` tinyint DEFAULT 0 COMMENT '0=待处理 1=已手动处理',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信队列补偿表';

SET FOREIGN_KEY_CHECKS = 1;
