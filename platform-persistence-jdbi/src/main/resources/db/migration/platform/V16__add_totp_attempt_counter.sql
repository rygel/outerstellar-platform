-- Track failed TOTP attempts independently of failed password attempts. The previous TOTP rate-limit
-- was per-partial-token (4 attempts) and resettable by re-authenticating with the correct password,
-- which minted a fresh partial token with attemptCount=0 — so an attacker with the password could
-- brute-force 6-digit TOTP codes indefinitely. This counter is per-user and drives account lockout
-- after N failures, independent of the partial-token lifecycle (issue #510).
ALTER TABLE plt_users ADD COLUMN IF NOT EXISTS failed_totp_attempts INT NOT NULL DEFAULT 0;
