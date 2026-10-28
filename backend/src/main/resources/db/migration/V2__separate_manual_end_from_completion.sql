-- 保留Flyway基线1原脚本；新增人工结束状态，不删除历史。
ALTER TABLE tasks DROP CHECK chk_plan_status;
ALTER TABLE tasks ADD CONSTRAINT chk_plan_status CHECK (status IN (0,1,2,3,4,5));
ALTER TABLE tasks MODIFY status int NOT NULL DEFAULT 0 COMMENT '0进行中,1学习成果完成,2未开始,3已取消,4已过期,5人工结束';

-- 旧版允许零进度直接完成。没有全部完成的真实步骤时，保留为人工结束，不能作为学习成果。
UPDATE tasks t
SET t.status = 5, t.version = t.version + 1
WHERE t.status = 1 AND (
    NOT EXISTS (SELECT 1 FROM sub_tasks s WHERE s.task_id = t.id)
    OR EXISTS (SELECT 1 FROM sub_tasks s WHERE s.task_id = t.id AND (s.current_progress IS NULL OR s.current_progress < 1))
);
