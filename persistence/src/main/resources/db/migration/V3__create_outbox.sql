CREATE TABLE outbox (
    id UUID PRIMARY KEY,
    payload_type VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    retry_count INT DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_outbox_unprocessed ON outbox(created_at, processed_at);
