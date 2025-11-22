-- V7: 카테고리 테이블 생성 및 게시글 카테고리 연동

-- 카테고리 테이블 생성
CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(200),
    display_order INT NOT NULL DEFAULT 0,
    create_at DATETIME(6) NOT NULL,
    update_at DATETIME(6) NOT NULL,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (name),
    INDEX idx_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 게시글 테이블에 category_id 컬럼 추가
ALTER TABLE posts ADD COLUMN category_id BIGINT NULL;

-- category_id 인덱스 추가
CREATE INDEX idx_post_category_id ON posts(category_id);

-- category_id 외래키 제약조건 추가
ALTER TABLE posts
ADD CONSTRAINT fk_post_category
FOREIGN KEY (category_id)
REFERENCES categories(id)
ON DELETE SET NULL;

-- 기본 카테고리 데이터 삽입
INSERT INTO categories (name, description, display_order, create_at, update_at) VALUES
('일반', '일반 게시글', 1, NOW(), NOW()),
('공지사항', '공지사항 게시글', 0, NOW(), NOW()),
('질문', '질문 게시글', 2, NOW(), NOW()),
('자유', '자유 게시글', 3, NOW(), NOW());

