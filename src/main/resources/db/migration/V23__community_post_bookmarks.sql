CREATE TABLE community_post_bookmarks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_community_post_bookmarks_post_user (post_id, user_id),
    CONSTRAINT fk_community_post_bookmarks_post FOREIGN KEY (post_id)
        REFERENCES community_posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_post_bookmarks_user FOREIGN KEY (user_id)
        REFERENCES community_users (id) ON DELETE RESTRICT
);
