-- Indexes for high-traffic query paths identified during performance review.

-- plt_messages: listDirtyMessages() filters by dirty=true AND deleted_at IS NULL
CREATE INDEX IF NOT EXISTS idx_plt_messages_dirty
    ON plt_messages(dirty, deleted_at);

-- plt_contacts: listDirtyContacts() filters by dirty=true
CREATE INDEX IF NOT EXISTS idx_plt_contacts_dirty
    ON plt_contacts(dirty);

-- plt_sessions: looked up by token_hash during every authenticated request
CREATE INDEX IF NOT EXISTS idx_plt_sessions_token_hash
    ON plt_sessions(token_hash);

-- plt_sessions: cleaned up by user_id on password change (invalidate all)
CREATE INDEX IF NOT EXISTS idx_plt_sessions_user_id
    ON plt_sessions(user_id);

-- plt_password_reset_tokens: looked up by token during password reset flow
CREATE INDEX IF NOT EXISTS idx_plt_password_reset_tokens_token
    ON plt_password_reset_tokens(token);

-- plt_password_reset_tokens: cleaned up by user_id on password change
CREATE INDEX IF NOT EXISTS idx_plt_password_reset_tokens_user_id
    ON plt_password_reset_tokens(user_id);
