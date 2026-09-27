package com.kraft.comment.dto;

/**
 * 댓글 삭제 요청의 결과(개선 보고서 A-BE-06). {@code softDeleted}가 true면 답글이 남아 있어
 * 행은 그대로 두고 내용만 비운 것이고(화면은 목록에서 그 댓글을 지우지 않고 "삭제된
 * 댓글입니다"로 바꿔 보여줘야 한다), false면 행 자체가 지워진 것이다(화면은 지금처럼 목록에서
 * 통째로 제거한다).
 */
public record CommentDeleteResultDto(Long id, boolean softDeleted) {
}
