-- KO-B08/DB-IDX-01: the default post list and both home queries pass category = NULL, so the
-- category-leading composite index cannot be used and MariaDB backward-scans
-- idx_community_posts_created while filtering status per row. Add the status-leading path;
-- keep idx_community_posts_category_status_created for the filtered list.
CREATE INDEX idx_community_posts_status_created
    ON community_posts (status, created_at DESC, id DESC);

-- DB-IDX-02: top-level comment pagination filters on (post_id, parent_id IS NULL) and orders by
-- (created_at ASC, id ASC) — CommunityCommentService builds that Sort explicitly. Neither
-- idx_community_comments_post_created nor idx_community_comments_parent covers that shape.
CREATE INDEX idx_community_comments_post_parent_created
    ON community_comments (post_id, parent_id, created_at, id);

-- DB-REC-01: the owner-scoped history list only had the plain FK index on owner_user_id (V33),
-- so every /api/v1/community/me/recommendation-sets did a filesort. Mirror the client-token
-- index shape that V19 already established for the anonymous path, with an id tie-breaker.
-- Verified locally (information_schema.statistics before/after): once this composite exists,
-- MariaDB reassigns the FK's index requirement to it and drops the now-redundant single-column
-- fk_recommendation_sets_owner index on its own — no explicit DROP INDEX needed or possible here
-- (attempting one throws ER_CANT_DROP_FIELD_OR_KEY once the FK already points at the composite).
CREATE INDEX idx_recommendation_sets_owner_created
    ON recommendation_sets (owner_user_id, created_at DESC, id DESC);
