-- 历史数据仅能按迁移时仍存在的步骤权重估算；已解除关联的记录保持NULL，不能伪造权重。
ALTER TABLE action_logs ADD COLUMN plan_contribution decimal(11,8) NULL;
UPDATE action_logs a JOIN sub_tasks s ON s.id = a.sub_task_id
SET a.plan_contribution = COALESCE(a.contribution, 0) * s.weight;
