-- V1: 초기 스키마 생성
-- 사용자(users) 테이블 생성

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL,
    create_at DATETIME(6) NOT NULL,
    update_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (name),
    UNIQUE KEY uk_user_email (email),
    INDEX idx_user_name (name),
    INDEX idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

