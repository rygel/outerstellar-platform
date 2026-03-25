-- Add missing indexes based on query patterns in JDBI repositories.

-- Vote lookups by user (FeedService.enrichPosts)
CREATE INDEX idx_stl_votes_user_target ON stl_votes(user_id, target_type, target_id);

-- Saved item lookups by user (FeedService.enrichPosts)
CREATE INDEX idx_stl_saved_items_user ON stl_saved_items(user_id, target_type, target_id);

-- Comment tree building (parent lookup)
CREATE INDEX idx_stl_comments_post_parent ON stl_comments(post_id, parent_comment_id);

-- Post filtering (is_removed is in every query WHERE clause)
CREATE INDEX idx_stl_posts_removed_created ON stl_posts(is_removed, created_at DESC);

-- Subscription lookup by user
CREATE INDEX idx_stl_subscriptions_user ON stl_subscriptions(user_id);
