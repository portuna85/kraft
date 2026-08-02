ALTER TABLE community_posts
    DROP FOREIGN KEY fk_community_posts_owner;

ALTER TABLE community_posts
    MODIFY COLUMN owner_id BIGINT NULL;

ALTER TABLE community_posts
    ADD CONSTRAINT fk_community_posts_owner FOREIGN KEY (owner_id)
        REFERENCES community_users (id) ON DELETE RESTRICT;

ALTER TABLE community_comments
    DROP FOREIGN KEY fk_community_comments_owner;

ALTER TABLE community_comments
    MODIFY COLUMN owner_id BIGINT NULL;

ALTER TABLE community_comments
    ADD CONSTRAINT fk_community_comments_owner FOREIGN KEY (owner_id)
        REFERENCES community_users (id) ON DELETE RESTRICT;

ALTER TABLE community_users
    DROP COLUMN withdrawn_at;
