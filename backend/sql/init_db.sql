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
  `status` tinyint DEFAULT '0' COMMENT '状态: 0-进行中, 1-已完成',
  `total_progress` decimal(5,2) DEFAULT '0.00' COMMENT '当前总进度百分比 (0-100.00)',
  `ai_suggestion` text COMMENT 'AI 对该目标的初步分析或建议',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主任务(目标)表';

-- ----------------------------
-- 3. 子任务表 (sub_tasks)
-- 对应 UI：子任务贡献度、任务拆解详情
-- ----------------------------
DROP TABLE IF EXISTS `sub_tasks`;
CREATE TABLE `sub_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '所属主任务ID',
  `content` varchar(255) NOT NULL COMMENT '子任务内容',
  `weight` decimal(3,2) NOT NULL DEFAULT '0.00' COMMENT '权重 (0-1.00，AI生成)',
  `current_progress` decimal(5,2) DEFAULT '0.00' COMMENT '该子任务已完成进度 (0-100.00)',
  `is_completed` tinyint DEFAULT '0' COMMENT '是否已彻底完成: 0-否, 1-是',
  `order_num` int DEFAULT '0' COMMENT '排序展示顺序',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='子任务(AI拆解步骤)表';

-- ----------------------------
-- 4. 行为日志表 (action_logs)
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
-- 5. 用户画像与今日状态表 (user_profiles)
-- 对应 UI：顶部的紫色激励卡片、用户标签
-- ----------------------------
DROP TABLE IF EXISTS `user_profiles`;
CREATE TABLE `user_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `energy_level` int DEFAULT '100' COMMENT '今日能量值 (0-100)',
  `daily_quote` varchar(500) DEFAULT NULL COMMENT '今日AI生成的个性化寄语',
  `interest_tags` json DEFAULT NULL COMMENT '兴趣标签 (AI分析得出, JSON格式)',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户画像与实时状态表';

SET FOREIGN_KEY_CHECKS = 1;
