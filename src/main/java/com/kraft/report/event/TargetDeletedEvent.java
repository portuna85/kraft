package com.kraft.report.event;

import com.kraft.report.domain.ReportTargetType;

import java.util.List;

/**
 * 게시글·댓글이 <b>작성자 본인의 요청으로</b> 지워졌을 때(행이 지워졌거나, 소프트 삭제된 경우 모두)
 * {@code PostService.delete}·{@code CommentService.delete}가 발행한다(개선 보고서 A-BE-01).
 * 게시글은 이 시점에 소프트 삭제되므로 보관 기간 뒤의 영구 삭제({@code PostService.purge})는 다시
 * 발행하지 않는다.
 * <p>
 * {@link com.kraft.report.service.ReportService}가 이 이벤트를 직접 호출로 받지 않고 이벤트로
 * 받는 이유는 순환 의존 때문이다 — {@code ReportService}는 신고 처리 시 대상을 지우려고 이미
 * {@code PostService}·{@code CommentService}에 의존한다. 그 반대 방향(post/comment →
 * report)까지 직접 의존으로 더하면 순환이 생긴다.
 * <p>
 * 관리자가 신고를 처리하며 지운 경우는 이 이벤트를 발행하지 않는다 — {@code ReportService.resolve()}가
 * 이미 그 자리에서 관련 신고를 {@code RESOLVED}로 직접 처리하므로, 대상이 사라진 게 "작성자
 * 스스로"인지 "관리자 판단"인지가 이 두 경로로 자연히 나뉜다.
 */
public record TargetDeletedEvent(ReportTargetType targetType, List<Long> targetIds) {
}
