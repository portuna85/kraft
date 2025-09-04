-- src/main/resources/data-h2.sql

-- users 더미 (선택)
-- created_date/modified_date 는 JPA가 채우지만, 스크립트라면 CURRENT_TIMESTAMP 사용
INSERT INTO users (created_date, modified_date, email, name, picture, role)
VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo@kraft.com', 'Demo', NULL, 'USER');

-- posts 더미 (선택)
INSERT INTO posts (created_date, modified_date, author, content, title)
VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'demo@kraft.com', '본문입니다', '제목입니다');
