ALTER TABLE outbox ADD COLUMN status VARCHAR(20) DEFAULT 'PENDING';
UPDATE outbox SET status = 'PROCESSED' WHERE processed_at IS NOT NULL;

CREATE INDEX idx_outbox_status ON outbox(status);
