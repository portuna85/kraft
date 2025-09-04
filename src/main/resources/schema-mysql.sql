-- clean
DROP TABLE IF EXISTS posts;
DROP TABLE IF EXISTS users;

-- users
CREATE TABLE users (
                       id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                       created_date  DATETIME(6) NOT NULL,
                       modified_date DATETIME(6) NOT NULL,
                       email         VARCHAR(255) NOT NULL,
                       name          VARCHAR(255) NOT NULL,
                       picture       VARCHAR(255),
                       role          ENUM('GUEST','USER') NOT NULL,
                       UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- posts
CREATE TABLE posts (
                       id            BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                       created_date  DATETIME(6) NOT NULL,
                       modified_date DATETIME(6) NOT NULL,
                       author        VARCHAR(255),
                       content       LONGTEXT NOT NULL,
                       title         VARCHAR(500) NOT NULL,
                       KEY idx_posts_author (author)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
