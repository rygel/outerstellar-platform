-- =============================================================================
-- User profile enhancements
-- Adds avatar URL and notification preference columns to the users table.
-- =============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url                  VARCHAR(512);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS push_notifications_enabled  BOOLEAN NOT NULL DEFAULT TRUE;
