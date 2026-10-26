-- LifeTrack consolidated schema, MySQL >= 8.0.16, UTF-8.
-- First initialize an empty MySQL instance; existing valid data is retained on rerun.
-- This is the FINAL pre-Flyway baseline (version 1). Future changes belong in V2+.
-- Do not run this file against a database already managed by Flyway.
CREATE DATABASE IF NOT EXISTS lifetrack CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE lifetrack;
SET NAMES utf8mb4;
SET time_zone = '+08:00';
CREATE TABLE IF NOT EXISTS `users` (
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

CREATE TABLE IF NOT EXISTS `tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(100) NOT NULL COMMENT '目标/任务名称',
  `category` enum('学习', '娱乐', '休息', '运动', '琐事') NOT NULL DEFAULT '学习' COMMENT '任务分类',
  `start_date` date NOT NULL COMMENT '计划开始日期',
  `end_date` date NOT NULL COMMENT '计划结束日期',
  `target_days` int NOT NULL COMMENT '目标打卡天数',
  `status` int NOT NULL DEFAULT '0' COMMENT '状态: 0-进行中, 1-已完成, 2-未开始, 3-已取消, 4-已过期',
  `version` bigint NOT NULL DEFAULT 0,
  `total_progress` decimal(7,4) DEFAULT '0.0000' COMMENT '当前总进度比例 (0-1)',
  `ai_suggestion` text COMMENT 'AI 对该目标的初步分析或建议',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status` (`user_id`, `status`),
  KEY `idx_plan_period` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主任务(目标)表';

CREATE TABLE IF NOT EXISTS `check_in_records` (
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
  KEY `idx_check_task_date` (`task_id`, `check_in_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学习计划正式每日打卡记录';

CREATE TABLE IF NOT EXISTS `sub_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '所属主任务ID',
  `content` varchar(255) NOT NULL COMMENT '子任务内容',
  `weight` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '权重比例 (0-1，支持动态增减步骤)',
  `current_progress` decimal(7,4) DEFAULT '0.00' COMMENT '该子任务已完成进度 (0-1)',
  `is_completed` int DEFAULT '0' COMMENT '是否已彻底完成: 0-否, 1-是',
  `order_num` int DEFAULT '0' COMMENT '排序展示顺序',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='子任务(AI拆解步骤)表';

CREATE TABLE IF NOT EXISTS `action_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `task_id` bigint DEFAULT NULL COMMENT '匹配到的主任务ID (由AI判定)',
  `sub_task_id` bigint DEFAULT NULL COMMENT '匹配到的子任务ID (由AI判定)',
  `category` enum('学习','娱乐','休息','运动','琐事') NOT NULL,
  `duration_minutes` int DEFAULT 0,
  `raw_input` text NOT NULL COMMENT '用户原始输入(自然语言)',
  `contribution` decimal(7,4) DEFAULT '0.00' COMMENT 'AI判定的进度贡献增量',
  `ai_analysis` varchar(255) DEFAULT NULL COMMENT 'AI对该行为的一句话分析',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间(用于趋势图)',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `created_at`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户行为记录与AI分析日志';

CREATE TABLE IF NOT EXISTS `user_profiles` (
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

CREATE TABLE IF NOT EXISTS `deferred_learning_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `task_id` bigint NOT NULL,
  `content` varchar(500) NOT NULL,
  `duration_minutes` int NOT NULL,
  `recorded_at` datetime NOT NULL COMMENT '原始学习时间（Asia/Shanghai），不是补分析时间',
  `status` enum('APPLIED','PENDING','UNMATCHED') NOT NULL COMMENT 'PENDING/APPLIED/UNMATCHED',
  `last_analysis` text,
  PRIMARY KEY (`id`),
  KEY `idx_deferred_user_task_time` (`user_id`, `task_id`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='待补分析学习记录';

-- One-time convergence of pre-baseline installations; no record is removed.
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND COLUMN_NAME='version'), 'ALTER TABLE tasks ADD COLUMN version bigint NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND COLUMN_NAME='category'), 'ALTER TABLE action_logs ADD COLUMN category enum(''学习'',''娱乐'',''休息'',''运动'',''琐事'') NOT NULL DEFAULT ''学习''', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND COLUMN_NAME='duration_minutes'), 'ALTER TABLE action_logs ADD COLUMN duration_minutes int DEFAULT 0', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='check_in_records' AND COLUMN_NAME='automatic'), 'ALTER TABLE check_in_records ADD COLUMN automatic bit DEFAULT NULL', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
ALTER TABLE tasks MODIFY start_date date NOT NULL, MODIFY end_date date NOT NULL, MODIFY target_days int NOT NULL, MODIFY status int NOT NULL DEFAULT 0;
ALTER TABLE sub_tasks MODIFY current_progress decimal(7,4) DEFAULT 0, MODIFY is_completed int DEFAULT 0;
ALTER TABLE action_logs MODIFY contribution decimal(7,4) DEFAULT 0;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='uk_task_owner'), 'ALTER TABLE tasks ADD CONSTRAINT uk_task_owner UNIQUE (user_id,id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='fk_task_user'), 'ALTER TABLE tasks ADD CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='chk_plan_period'), 'ALTER TABLE tasks ADD CONSTRAINT chk_plan_period CHECK (start_date <= end_date)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='chk_plan_target'), 'ALTER TABLE tasks ADD CONSTRAINT chk_plan_target CHECK (target_days >= 1 AND target_days <= DATEDIFF(end_date,start_date)+1)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='chk_plan_progress'), 'ALTER TABLE tasks ADD CONSTRAINT chk_plan_progress CHECK (total_progress BETWEEN 0 AND 1)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND CONSTRAINT_NAME='chk_plan_status'), 'ALTER TABLE tasks ADD CONSTRAINT chk_plan_status CHECK (status IN (0,1,2,3,4))', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND CONSTRAINT_NAME='uk_step_plan'), 'ALTER TABLE sub_tasks ADD CONSTRAINT uk_step_plan UNIQUE (task_id,id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND CONSTRAINT_NAME='fk_sub_task'), 'ALTER TABLE sub_tasks ADD CONSTRAINT fk_sub_task FOREIGN KEY (task_id) REFERENCES tasks(id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND CONSTRAINT_NAME='chk_step_progress'), 'ALTER TABLE sub_tasks ADD CONSTRAINT chk_step_progress CHECK (current_progress BETWEEN 0 AND 1)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND CONSTRAINT_NAME='chk_step_weight'), 'ALTER TABLE sub_tasks ADD CONSTRAINT chk_step_weight CHECK (weight BETWEEN 0 AND 1)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND CONSTRAINT_NAME='chk_step_completed'), 'ALTER TABLE sub_tasks ADD CONSTRAINT chk_step_completed CHECK (is_completed IN (0,1))', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND CONSTRAINT_NAME='fk_log_user'), 'ALTER TABLE action_logs ADD CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND CONSTRAINT_NAME='fk_log_plan_owner'), 'ALTER TABLE action_logs ADD CONSTRAINT fk_log_plan_owner FOREIGN KEY (user_id,task_id) REFERENCES tasks(user_id,id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND CONSTRAINT_NAME='fk_log_step'), 'ALTER TABLE action_logs ADD CONSTRAINT fk_log_step FOREIGN KEY (sub_task_id) REFERENCES sub_tasks(id) ON DELETE SET NULL', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND CONSTRAINT_NAME='chk_log_contribution'), 'ALTER TABLE action_logs ADD CONSTRAINT chk_log_contribution CHECK (task_id IS NULL OR contribution BETWEEN 0 AND 1)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND CONSTRAINT_NAME='chk_log_duration'), 'ALTER TABLE action_logs ADD CONSTRAINT chk_log_duration CHECK (duration_minutes >= 0)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='check_in_records' AND CONSTRAINT_NAME='fk_check_plan_owner'), 'ALTER TABLE check_in_records ADD CONSTRAINT fk_check_plan_owner FOREIGN KEY (user_id,task_id) REFERENCES tasks(user_id,id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='check_in_records' AND CONSTRAINT_NAME='chk_duration_nonnegative'), 'ALTER TABLE check_in_records ADD CONSTRAINT chk_duration_nonnegative CHECK (duration_minutes >= 0)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='deferred_learning_records' AND CONSTRAINT_NAME='fk_deferred_plan_owner'), 'ALTER TABLE deferred_learning_records ADD CONSTRAINT fk_deferred_plan_owner FOREIGN KEY (user_id,task_id) REFERENCES tasks(user_id,id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='deferred_learning_records' AND CONSTRAINT_NAME='chk_deferred_duration'), 'ALTER TABLE deferred_learning_records ADD CONSTRAINT chk_deferred_duration CHECK (duration_minutes >= 0)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_profiles' AND CONSTRAINT_NAME='fk_profile_user'), 'ALTER TABLE user_profiles ADD CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users(id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND INDEX_NAME='idx_user_status'), 'CREATE INDEX idx_user_status ON tasks (user_id,status)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tasks' AND INDEX_NAME='idx_user_period'), 'CREATE INDEX idx_user_period ON tasks (user_id,start_date,end_date)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='sub_tasks' AND INDEX_NAME='idx_step_order'), 'CREATE INDEX idx_step_order ON sub_tasks (task_id,order_num)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND INDEX_NAME='idx_user_date'), 'CREATE INDEX idx_user_date ON action_logs (user_id,created_at)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='action_logs' AND INDEX_NAME='idx_task_id'), 'CREATE INDEX idx_task_id ON action_logs (task_id)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='check_in_records' AND INDEX_NAME='idx_check_user_date'), 'CREATE INDEX idx_check_user_date ON check_in_records (user_id,check_in_date)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='check_in_records' AND INDEX_NAME='idx_check_task_date'), 'CREATE INDEX idx_check_task_date ON check_in_records (task_id,check_in_date)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
SET @lt_ddl = IF(NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='deferred_learning_records' AND INDEX_NAME='idx_deferred_user_task_time'), 'CREATE INDEX idx_deferred_user_task_time ON deferred_learning_records (user_id,task_id,recorded_at)', 'SELECT 1');
PREPARE lt_stmt FROM @lt_ddl;
EXECUTE lt_stmt;
DEALLOCATE PREPARE lt_stmt;
