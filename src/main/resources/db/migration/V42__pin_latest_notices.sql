-- 목록 상단 고정이 "최신 NOTICE 5개"에서 "관리자가 기한을 정한 글(pinned_until)"로 바뀐다. 배포 순간
-- 화면이 달라지지 않도록, 지금 고정되어 보이던 글(삭제되지 않은 최신 공지 5개)을 먼저 기한 없이
-- (사실상 2099년까지) 고정해 둔다. 이후로 새 공지는 자동으로 고정되지 않고 관리자가 직접 고정한다.
-- MariaDB는 IN (... LIMIT)을 지원하지 않으므로 파생 테이블로 묶는다. 시각은 KST 기준이다.
UPDATE posts p
    JOIN (SELECT id FROM posts WHERE category = 'NOTICE' AND deleted_at IS NULL ORDER BY id DESC LIMIT 5) t
        ON p.id = t.id
SET p.pinned_until = '2099-12-31 23:59:59.000000';
