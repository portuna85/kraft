-- 고정 공지 상한(PostQueryService.PINNED_LIMIT)을 여러 요청·여러 프로세스에서 함께 지키기 위한 직렬화 지점이다.
-- "현재 고정 수를 센 뒤 고정한다"는 서로 다른 글을 동시에 고정하면 둘 다 여유가 있다고 판단할 수 있고, 게시글의 @Version은
-- 서로 다른 행 사이의 개수를 지켜 주지 못한다. 고정·복구·숨김 해제는 이 한 행을 SELECT ... FOR UPDATE로 잡은 뒤에야
-- 개수를 계산하고 반영하므로 한 번에 하나씩만 진행한다. 데이터를 담지 않는 잠금 전용 단일 행(id=1)이다.
CREATE TABLE post_pin_lock (
    id INT NOT NULL PRIMARY KEY
);

INSERT INTO post_pin_lock (id) VALUES (1);
