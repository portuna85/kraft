-- V5: Ensure created_at exists on posts and users tables (idempotent)
-- Adds created_at column if missing to avoid Hibernate schema-validation failures

-- For MariaDB 10.3+ ADD COLUMN IF NOT EXISTS is supported
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE posts
  ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Ensure modified_at exists as well (safety)
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE posts
  ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- If created_at exists but is NULL for any row, set to CURRENT_TIMESTAMP as fallback
UPDATE users SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE created_at IS NULL;
UPDATE posts SET created_at = COALESCE(created_at, CURRENT_TIMESTAMP) WHERE created_at IS NULL;

