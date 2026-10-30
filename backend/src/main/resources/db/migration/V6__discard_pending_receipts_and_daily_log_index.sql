-- 保留已移除记录的提交凭证，防止旧requestId重放后重新创建；正文由业务删除入口清空。
ALTER TABLE deferred_learning_records MODIFY status enum('APPLIED','PENDING','UNMATCHED','DISCARDED') NOT NULL;
CREATE INDEX idx_log_user_plan_day ON action_logs (user_id, task_id, created_at, id);
