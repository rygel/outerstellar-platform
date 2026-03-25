-- Search indexes for common query patterns.
--
-- H2 does not support functional/expression indexes like LOWER(column).
-- These plain indexes still help the query planner with ORDER BY and range scans.
--
-- For production PostgreSQL deployments, consider adding:
--   CREATE INDEX idx_stl_posts_title_trgm ON stl_posts USING gin (title gin_trgm_ops);
--   CREATE INDEX idx_stl_communities_name_trgm ON stl_communities USING gin (name gin_trgm_ops);
-- after enabling: CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_stl_posts_title ON stl_posts(title);
CREATE INDEX idx_stl_communities_display ON stl_communities(display_name);
