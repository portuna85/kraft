-- 이미지 소유권 대장(post_images)과 게시글 낙관적 잠금 버전(posts.version).
--
-- post_images는 "이 파일을 누가 올렸고 어느 게시글이 쓰는가"를 기록한다. 이 기록이 없을 때는
-- Post.picture 문자열만 믿을 수밖에 없어서, 자기 게시글에 남의 이미지 URL을 넣고 그 글을
-- 지우면 남의 파일이 사라졌다(개선 보고서 F01). status는 삭제를 "예약"으로 남겨 실제 파일
-- 삭제를 DB 커밋 이후로 미루고, 실패 시 재시도할 근거가 된다(F05).

-- status를 ENUM으로 두는 이유는 V1의 users.role 주석 참고(Hibernate가 MariaDB에서 네이티브
-- ENUM을 기대하므로 VARCHAR면 ddl-auto: validate가 기동을 막는다).
-- 값 목록은 Hibernate가 만드는 것과 같은 알파벳 순이다.
CREATE TABLE post_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    file_name VARCHAR(200) NOT NULL,
    owner_id BIGINT NOT NULL,
    post_id BIGINT,
    status ENUM('ATTACHED','ORPHAN','PENDING_DELETE') NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK_POST_IMAGE_FILE_NAME UNIQUE (file_name),
    CONSTRAINT FK_POST_IMAGES_OWNER FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT FK_POST_IMAGES_POST FOREIGN KEY (post_id) REFERENCES posts (id)
) ENGINE=InnoDB;

-- 정리 작업이 status로 훑고, 미연결 만료 정리는 created_at까지 함께 본다.
CREATE INDEX IX_POST_IMAGES_STATUS_CREATED_AT ON post_images (status, created_at);

-- 낙관적 잠금 버전. 기존 게시글은 0에서 시작한다. @Version 컬럼은 null을 허용하면
-- Hibernate가 "분리된 엔티티"로 오해할 수 있으므로 NOT NULL DEFAULT 0으로 둔다.
ALTER TABLE posts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
