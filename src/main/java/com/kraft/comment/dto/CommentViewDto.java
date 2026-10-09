package com.kraft.comment.dto;

import com.kraft.KraftApplication;
import com.kraft.comment.domain.Comment;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 댓글 <b>화면 전용</b> 응답(커서 페이지 API가 이 형태로만 응답). 댓글별 {@code canManage}로 관리 버튼 노출을 서버
 * 판정에 맞춘다. 최상위 댓글({@code parentId}가 null)은 처음 로드된 답글 일부를 {@code replies}에 싣고,
 * {@code replyCount}는 실제 총 답글 수, {@code hasMoreReplies}가 true면
 * {@code GET /api/v1/comments/{parentId}/replies}로 이어 받는다. 답글 항목의 {@code replies}·{@code replyCount}·
 * {@code hasMoreReplies}는 항상 빈 값이다(2단계까지).
 */
public record CommentViewDto(
        Long id,
        Long postId,
        Long parentId,
        String content,
        String author,
        /** 서버 시간대 오프셋을 붙여 보낸다 — 오프셋 없는 LocalDateTime은 클라이언트가 브라우저 로컬 시간으로 해석하고, 직접 만든 UTC 값과 어긋난다. */
        OffsetDateTime createdAt,
        boolean canManage,
        List<CommentViewDto> replies,
        long replyCount,
        boolean hasMoreReplies,
        /** 편집 충돌 감지용 낙관적 잠금 버전. 저장 요청에 그대로 실어 보내면 그 사이 다른 저장이 있었을 때 409로 거절된다. */
        Long version,
        /** 답글이 있어 행은 남기고 내용만 비운 댓글. true면 {@code content}는 빈 문자열이고 화면은 "삭제된 댓글입니다"로 바꿔 수정·삭제 버튼을 숨긴다. */
        boolean deleted,
        /** 관리자가 숨긴 댓글. 관리자가 아니면 {@code content}가 빈 문자열이고 {@code canManage}도 false다(화면은 "관리자가 숨긴 댓글입니다"). 관리자는 원문과 해제 버튼을 본다. */
        boolean blinded,
        /** 관리자 권한. 숨김 해제 같은 관리 버튼 노출에 쓴다 — 서버가 판정한 값만 믿는다. */
        boolean canModerate
) {

    /** 숨김·관리 정보 없이 만드는 편의 생성자(테스트와 응답 조립용). */
    public CommentViewDto(Long id, Long postId, Long parentId, String content, String author,
                          OffsetDateTime createdAt, boolean canManage, List<CommentViewDto> replies,
                          long replyCount, boolean hasMoreReplies, Long version, boolean deleted) {
        this(id, postId, parentId, content, author, createdAt, canManage, replies, replyCount, hasMoreReplies,
                version, deleted, false, false);
    }

    /** 최상위 댓글·답글 생성용(저장·수정 응답). 답글 목록은 비워 두고, 서비스가 나중에 채운다. */
    public CommentViewDto(Comment entity, boolean canManage) {
        this(entity, canManage, false, List.of(), 0L, false);
    }

    /** 관리자 여부를 모르는 호출은 일반 사용자로 본다 — 숨겨진 댓글의 내용이 새지 않는 쪽이 안전한 기본값이다. */
    public CommentViewDto(Comment entity, boolean canManage, List<CommentViewDto> replies,
                           long replyCount, boolean hasMoreReplies) {
        this(entity, canManage, false, replies, replyCount, hasMoreReplies);
    }

    public CommentViewDto(Comment entity, boolean canManage, boolean viewerIsAdmin, List<CommentViewDto> replies,
                           long replyCount, boolean hasMoreReplies) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getParent() != null ? entity.getParent().getId() : null,
                hiddenFrom(entity, viewerIsAdmin) ? "" : entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt() != null
                        ? entity.getCreatedAt().atZone(ZoneId.of(KraftApplication.ZONE_ID)).toOffsetDateTime()
                        : null,
                canManage && !hiddenFrom(entity, viewerIsAdmin),
                replies,
                replyCount,
                hasMoreReplies,
                entity.getVersion(),
                entity.isDeleted(),
                entity.isBlinded(),
                viewerIsAdmin
        );
    }

    /** 숨겨진 댓글을 관리자가 아닌 사람이 보는 경우. 내용과 관리 권한을 모두 가린다. */
    private static boolean hiddenFrom(Comment entity, boolean viewerIsAdmin) {
        return entity.isBlinded() && !viewerIsAdmin;
    }

    /** 답글 목록·개수를 채운 새 인스턴스를 돌려준다. record는 불변이라 필드만 바꿔 복제한다. */
    public CommentViewDto withReplies(List<CommentViewDto> newReplies, long newReplyCount, boolean newHasMoreReplies) {
        return new CommentViewDto(id, postId, parentId, content, author, createdAt, canManage,
                newReplies, newReplyCount, newHasMoreReplies, version, deleted, blinded, canModerate);
    }
}
