ALTER TABLE plt_users ADD COLUMN totp_secret VARCHAR(64);
ALTER TABLE plt_users ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE plt_users ADD COLUMN totp_backup_codes TEXT;
