-- Preserve history; old learning-only COMPLETED plans remain executable only in their valid period.
UPDATE tasks t SET t.status = CASE
    WHEN t.end_date < DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')) THEN 4
    WHEN t.start_date > DATE(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')) THEN 2
    ELSE 0 END,
    t.version = t.version + 1
WHERE t.status = 1 AND (
    NOT EXISTS (SELECT 1 FROM sub_tasks s WHERE s.task_id=t.id)
    OR EXISTS (SELECT 1 FROM sub_tasks s WHERE s.task_id=t.id AND s.current_progress < 1)
    OR (SELECT COUNT(*) FROM check_in_records c WHERE c.task_id=t.id AND c.user_id=t.user_id
        AND c.check_in_date BETWEEN t.start_date AND t.end_date) < t.target_days
);
