ALTER TABLE messages ADD COLUMN sync_conflict TEXT;
COMMENT ON COLUMN messages.sync_conflict IS 'Stores the serialized server version of the message when a sync conflict occurs';
