CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_plt_messages_content_trgm
    ON plt_messages USING GIN (content gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_plt_contacts_name_trgm
    ON plt_contacts USING GIN (name gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_plt_contacts_company_trgm
    ON plt_contacts USING GIN (company gin_trgm_ops);
