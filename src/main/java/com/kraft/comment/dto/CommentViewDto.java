package com.kraft.comment.dto;

import com.kraft.comment.domain.Comment;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 댓글 <b>화면 전용</b> 응답. 커서 페이지 API가 이 형태로만 응답하며, 화면에만 댓글별
 * {@code canManage}를 내려 관리 버튼 노출을 서버 판정에 맞춘다.
 * <p>
 * {@code parentId}가 null이면 최상위 댓글이고, {@code replies}에 처음 로드된 답글 일부가
 * 실린다. {@code replyCount}는 그 부모의 실제 총 답글 수이고, {@code hasMoreReplies}가
 * true면 {@code replies}에 다 담지 못한 답글이 더 있다는 뜻이다(개선 보고서 COR-05) — 화면은
 * 이때 {@code GET /api/v1/comments/{parentId}/replies}로 이어서 받아 온다. 답글 자신은
 * 2단계까지만 허용하므로 답글 항목의 {@code replies}·{@code replyCount}·{@code hasMoreReplies}는
 * 항상 빈 값이다.
 */
public record CommentViewDto(
        Long id,
        Long postId,
        Long parentId,
        String content,
        String author,
        /**
         * 서버 시간대의 오프셋을 실어 보낸다(개선 보고서 COR-08). {@code Comment.createdAt}은
         * DB·서버 저장용 {@code LocalDateTime}이라 오프셋이 없다 — 그 값을 오프셋 없이 그대로
         * JSON으로 내려보내면, 클라이언트의 {@code new Date(iso)}가 그 문자열을 "브라우저의"
         * 로컬 시간으로 해석한다. 반면 화면이 새 댓글을 즉시 반영할 때 직접 만든
         * {@code new Date().toISOString()}은 UTC다 — 같은 순간인데 새로고침 전후로 다르게
         * 표시됐다. 서버 시간대로 명시적인 오프셋을 붙이면 클라이언트가 어느 경로로 값을
         * 받든 같은 순간으로 해석한다.
         */
        OffsetDateTime createdAt,
        boolean canManage,
        List<CommentViewDto> replies,
        long replyCount,
        boolean hasMoreReplies,
        /**
         * 편집 충돌 감지에 쓰는 낙관적 잠금 버전(B12). 저장 요청의 {@code version}에 이 값을
         * 그대로 실어 보내면, 그 사이 다른 저장이 있었을 때 서버가 409로 거절한다.
         */
        Long version
) {

    /** 최상위 댓글·답글 생성용(저장·수정 응답). 답글 목록은 비워 두고, 서비스가 나중에 채운다. */
    public CommentViewDto(Comment entity, boolean canManage) {
        this(entity, canManage, List.of(), 0L, false);
    }

    public CommentViewDto(Comment entity, boolean canManage, List<CommentViewDto> replies,
                           long replyCount, boolean hasMoreReplies) {
        this(
                entity.getId(),
                entity.getPost() != null ? entity.getPost().getId() : null,
                entity.getParent() != null ? entity.getParent().getId() : null,
                entity.getContent(),
                entity.getUser() != null ? entity.getUser().getName() : null,
                entity.getCreatedAt() != null
                        ? entity.getCreatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime()
                        : null,
                canManage,
                replies,
                replyCount,
                hasMoreReplies,
                entity.getVersion()
        );
    }

    /** 답글 목록·개수를 채운 새 인스턴스를 돌려준다. record는 불변이라 필드만 바꿔 복제한다. */
    public CommentViewDto withReplies(List<CommentViewDto> newReplies, long newReplyCount, boolean newHasMoreReplies) {
        return new CommentViewDto(id, postId, parentId, content, author, createdAt, canManage,
                newReplies, newReplyCount, newHasMoreReplies, version);
    }
}
