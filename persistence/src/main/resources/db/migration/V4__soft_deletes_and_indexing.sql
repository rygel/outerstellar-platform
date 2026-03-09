ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE outbox ADD COLUMN deleted_at TIMESTAMP;

-- Full-text search optimization for H2 (Lucene or simple indexes)
-- Since we're using H2, we can't use PostgreSQL GIN/GiST directly.
-- However, we can add standard indexes to speed up common filter and sort patterns.
CREATE INDEX idx_messages_author ON messages(author);
CREATE INDEX idx_messages_created_at ON messages(created_at);
CREATE INDEX idx_messages_deleted_at ON messages(deleted_at);

-- H2 doesn't support functional indexes in the same way as Postgres or Oracle for some expressions.
-- Instead, we can add a generated column and index that.
ALTER TABLE messages ADD COLUMN created_year INT AS (YEAR(created_at));
CREATE INDEX idx_messages_year ON messages(created_year);
