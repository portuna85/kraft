-- 업로드할 때 서버가 읽은 실제 픽셀 크기. 지금까지 게시글의 picture_width/height는 클라이언트가 보낸 값을
-- 그대로 저장해, 조작된 값이 <img width height>(레이아웃 계산)에 쓰일 수 있었다.
-- 기존 행은 값이 없으므로 NULL을 허용한다(그 이미지가 붙은 글은 지금 저장된 값을 그대로 쓴다).
ALTER TABLE post_images
    ADD COLUMN width INT NULL,
    ADD COLUMN height INT NULL;
