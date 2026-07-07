-- Drop the non-unique btree on plt_password_reset_tokens.token — it duplicates the
-- plt_password_reset_tokens_token_key UNIQUE constraint (created by the NOT NULL UNIQUE
-- column definition in V1), which already provides the btree(token) lookup used by
-- WHERE token = :token. Keeping both doubled write amplification on the reset-token path
-- and wasted storage. idx_plt_password_reset_tokens_user_id is retained (no user-lookup
-- unique constraint exists).
DROP INDEX IF EXISTS idx_plt_password_reset_tokens_token;
