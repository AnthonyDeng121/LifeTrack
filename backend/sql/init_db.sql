/*
 Navicat Premium Data Transfer
 Target Server Type    : MySQL
 Target Server Version : 80000
 File Encoding         : 65001
 Project: LifeTrack AI
 注意建库时的字符集选择：
 Charset: utf8mb4
 Collation: utf8mb4_0900_ai_ci
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户表 (users)
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `openid` varchar(100) DEFAULT NULL COMMENT '微信OpenID',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户基础信息表';

-- ----------------------------
-- 2. 主任务表 (tasks)
-- 对应 UI：今日总进度、分类统计
-- ----------------------------
DROP TABLE IF EXISTS `tasks`;
CREATE TABLE `tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(100) NOT NULL COMMENT '目标/任务名称',
  `category` enum('学习', '娱乐', '休息', '运动', '琐事') DEFAULT '学习' COMMENT '任务分类',
  `start_date` date NOT NULL COMMENT '计划开始日期',
  `end_date` date NOT NULL COMMENT '计划结束日期',
  `target_days` int NOT NULL COMMENT '目标打卡天数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-进行中, 1-已完成, 2-未开始, 3-已取消, 4-已过期',
  `total_progress` decimal(7,4) DEFAULT '0.0000' COMMENT '当前总进度比例 (0-1)',
  `ai_suggestion` text COMMENT 'AI 对该目标的初步分析或建议',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_plan_period` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主任务(目标)表';

-- ----------------------------
-- 3. 正式学习打卡记录表
-- ----------------------------
DROP TABLE IF EXISTS `check_in_records`;
CREATE TABLE `check_in_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '打卡用户ID',
  `task_id` bigint NOT NULL COMMENT '学习计划ID',
  `check_in_date` date NOT NULL COMMENT '业务打卡日期（Asia/Shanghai）',
  `content` varchar(500) NOT NULL COMMENT '打卡内容',
  `duration_minutes` int NOT NULL DEFAULT '0' COMMENT '学习时长（分钟）',
  `mood` varchar(20) DEFAULT NULL COMMENT '打卡心情',
  `automatic` bit DEFAULT NULL COMMENT '是否由有效学习记录自动打卡，旧版手动记录为NULL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_task_check_date` (`user_id`, `task_id`, `check_in_date`),
  KEY `idx_check_user_date` (`user_id`, `check_in_date`),
  KEY `idx_check_task_date` (`task_id`, `check_in_date`),
  CONSTRAINT `fk_check_task` FOREIGN KEY (`task_id`) REFERENCES `tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_duration_nonnegative` CHECK (`duration_minutes` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习计划正式每日打卡记录';

-- ----------------------------
-- 4. 子任务表 (sub_tasks)
-- 对应 UI：子任务贡献度、任务拆解详情
-- ----------------------------
DROP TABLE IF EXISTS `sub_tasks`;
CREATE TABLE `sub_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '所属主任务ID',
  `content` varchar(255) NOT NULL COMMENT '子任务内容',
  `weight` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '权重比例 (0-1，支持动态增减步骤)',
  `current_progress` decimal(5,2) DEFAULT '0.00' COMMENT '该子任务已完成进度 (0-100.00)',
  `is_completed` tinyint DEFAULT '0' COMMENT '是否已彻底完成: 0-否, 1-是',
  `order_num` int DEFAULT '0' COMMENT '排序展示顺序',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='子任务(AI拆解步骤)表';

-- ----------------------------
-- 5. 行为日志表 (action_logs)
-- 对应 UI：本周成长趋势、今日时间分布、行为流
-- ----------------------------
DROP TABLE IF EXISTS `action_logs`;
CREATE TABLE `action_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_id` bigint DEFAULT NULL COMMENT '匹配到的主任务ID (由AI判定)',
  `sub_task_id` bigint DEFAULT NULL COMMENT '匹配到的子任务ID (由AI判定)',
  `raw_input` text NOT NULL COMMENT '用户原始输入(自然语言)',
  `contribution` decimal(5,2) DEFAULT '0.00' COMMENT 'AI判定的进度贡献增量',
  `ai_analysis` varchar(255) DEFAULT NULL COMMENT 'AI对该行为的一句话分析',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间(用于趋势图)',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `created_at`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户行为记录与AI分析日志';

-- ----------------------------
-- 6. 用户画像与今日状态表 (user_profiles)
-- 对应 UI：顶部的紫色激励卡片、用户标签
-- ----------------------------
DROP TABLE IF EXISTS `user_profiles`;
CREATE TABLE `user_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `energy_level` int DEFAULT '100' COMMENT '今日能量值 (0-100)',
  `current_anxiety_level` int DEFAULT '1' COMMENT '当前焦虑等级 (1-10)',
  `daily_quote` varchar(500) DEFAULT NULL COMMENT '今日AI生成的个性化寄语',
  `interest_tags` json DEFAULT NULL COMMENT '兴趣标签 (AI分析得出, JSON格式)',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像与实时状态表';

-- 7. AI故障期间持久化的待分析学习记录
DROP TABLE IF EXISTS `deferred_learning_records`;
CREATE TABLE `deferred_learning_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `content` varchar(500) NOT NULL,
  `duration_minutes` int NOT NULL,
  `recorded_at` datetime NOT NULL COMMENT '原始学习时间（Asia/Shanghai），不是补分析时间',
  `status` varchar(20) NOT NULL COMMENT 'PENDING/APPLIED/UNMATCHED',
  `last_analysis` text,
  PRIMARY KEY (`id`),
  KEY `idx_deferred_user_task_time` (`user_id`, `task_id`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待补分析学习记录';

SET FOREIGN_KEY_CHECKS = 1;
