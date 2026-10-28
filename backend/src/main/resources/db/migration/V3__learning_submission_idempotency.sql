ALTER TABLE deferred_learning_records ADD COLUMN request_id varchar(36) NULL;
ALTER TABLE deferred_learning_records ADD CONSTRAINT uk_learning_submission UNIQUE (user_id, request_id);
CREATE INDEX idx_deferred_pending_page ON deferred_learning_records (user_id, status, recorded_at, id);
CREATE INDEX idx_check_history_page ON check_in_records (user_id, check_in_date, created_at, id);
