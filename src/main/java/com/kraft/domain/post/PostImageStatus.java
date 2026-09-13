package com.kraft.domain.post;

/**
 * 업로드된 이미지 파일 한 개의 생명주기 상태.
 * <p>
 * 값 목록은 Hibernate가 MariaDB에 만드는 네이티브 ENUM과 같도록 알파벳 순으로 쓴다
 * (V1__baseline.sql의 users.role 주석 참고 — 순서가 다르면 운영의 ddl-auto: validate가 기동을 막는다).
 */
public enum PostImageStatus {

    /** 게시글에 연결됨. 그 게시글이 지워질 때까지 유지한다. */
    ATTACHED,

    /** 업로드만 되고 아직 어떤 게시글에도 연결되지 않음. 일정 시간이 지나면 정리 대상이다. */
    ORPHAN,

    /**
     * 삭제 예정. DB 커밋이 끝난 뒤 실제 파일을 지운다 — 트랜잭션 안에서 파일을 먼저 지우면
     * 롤백되어도 파일이 돌아오지 않기 때문이다(개선 보고서 F05).
     */
    PENDING_DELETE
}
