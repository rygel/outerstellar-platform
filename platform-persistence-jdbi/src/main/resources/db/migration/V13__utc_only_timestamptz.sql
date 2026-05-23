-- =============================================================================
-- Convert all TIMESTAMP (without time zone) columns to TIMESTAMPTZ.
-- See ADR-0001: UTC-only timestamps in the database.
--
-- All existing data is assumed to be stored in UTC. The USING clause
-- tells PostgreSQL to interpret the current bare timestamp values as UTC
-- before attaching the timezone.
-- =============================================================================

-- plt_messages
ALTER TABLE plt_messages ALTER COLUMN created_at  TYPE TIMESTAMPTZ USING created_at  AT TIME ZONE 'UTC';
ALTER TABLE plt_messages ALTER COLUMN deleted_at  TYPE TIMESTAMPTZ USING deleted_at  AT TIME ZONE 'UTC';

-- plt_outbox
ALTER TABLE plt_outbox ALTER COLUMN created_at   TYPE TIMESTAMPTZ USING created_at   AT TIME ZONE 'UTC';
ALTER TABLE plt_outbox ALTER COLUMN processed_at TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC';
ALTER TABLE plt_outbox ALTER COLUMN deleted_at   TYPE TIMESTAMPTZ USING deleted_at   AT TIME ZONE 'UTC';

-- plt_users (last_activity_at is TIMESTAMP; created_at and locked_until are already TIMESTAMPTZ)
ALTER TABLE plt_users ALTER COLUMN last_activity_at TYPE TIMESTAMPTZ USING last_activity_at AT TIME ZONE 'UTC';

-- plt_contacts
ALTER TABLE plt_contacts ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- plt_audit_log
ALTER TABLE plt_audit_log ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- plt_sessions
ALTER TABLE plt_sessions ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE plt_sessions ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';

-- plt_password_reset_tokens
ALTER TABLE plt_password_reset_tokens ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC';
ALTER TABLE plt_password_reset_tokens ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- plt_api_keys
ALTER TABLE plt_api_keys ALTER COLUMN created_at   TYPE TIMESTAMPTZ USING created_at   AT TIME ZONE 'UTC';
ALTER TABLE plt_api_keys ALTER COLUMN last_used_at TYPE TIMESTAMPTZ USING last_used_at AT TIME ZONE 'UTC';

-- plt_oauth_connections
ALTER TABLE plt_oauth_connections ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- plt_device_tokens
ALTER TABLE plt_device_tokens ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
ALTER TABLE plt_device_tokens ALTER COLUMN last_seen  TYPE TIMESTAMPTZ USING last_seen  AT TIME ZONE 'UTC';

-- plt_notifications
ALTER TABLE plt_notifications ALTER COLUMN read_at    TYPE TIMESTAMPTZ USING read_at    AT TIME ZONE 'UTC';
ALTER TABLE plt_notifications ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
