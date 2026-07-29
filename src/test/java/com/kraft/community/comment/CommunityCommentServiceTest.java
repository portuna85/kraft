package com.kraft.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kraft.common.error.ApiException;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import com.kraft.community.post.PostStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

/**
 * Blitz CommentsServiceTest.concurrentPostDeletionDuringCreateIsTranslatedToPostNotFound의
 * 의도(FK race → 404 변환)를 Testcontainers 없이 Mockito로 재현한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommunityCommentService 단위 테스트")
class CommunityCommentServiceTest {

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityPostMetricsRepository communityPostMetricsRepository;

    private CommunityCommentService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);
        service = new CommunityCommentService(communityCommentRepository, communityPostRepository,
                communityPostMetricsRepository, clock, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("저장 직전 게시글이 동시 삭제되면 404로 변환한다")
    void concurrentPostDeletion_isTranslatedToPostNotFound() {
        CommunityPost post = new CommunityPost(1L, "작성자", "제목", "내용",
                com.kraft.community.post.PostCategory.GENERAL, null,
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        when(communityPostRepository.findById(10L)).thenReturn(Optional.of(post));
        when(communityCommentRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("FK violation"));

        assertThatThrownBy(() -> service.create(1L, "작성자", 10L, new CreateCommentRequest("댓글", null)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "COMMUNITY_POST_NOT_FOUND")
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }

    private static CommunityPost publishedPost() {
        return new CommunityPost(1L, "작성자", "제목", "내용", PostCategory.GENERAL, null,
                OffsetDateTime.now(), OffsetDateTime.now());
    }

    private static CommunityPost hiddenPost() {
        try {
            CommunityPost post = publishedPost();
            var field = CommunityPost.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(post, PostStatus.HIDDEN_BY_AUTHOR);
            return post;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("B-P0-7: 존재하지 않는 게시글의 댓글 목록을 요청하면 404를 던진다")
    void list_nonexistentPost_throwsNotFound() {
        given(communityPostRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(10L, 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo("COMMUNITY_POST_NOT_FOUND");
                });
        verify(communityCommentRepository, never()).findByPostIdAndParentIdIsNull(anyLong(), any());
    }

    @Test
    @DisplayName("B-P0-7: 숨김 게시글의 댓글 목록을 요청하면 404를 던진다(공개 엔드포인트로 계속 읽히던 결함)")
    void list_hiddenPost_throwsNotFound() {
        given(communityPostRepository.findById(10L)).willReturn(Optional.of(hiddenPost()));

        assertThatThrownBy(() -> service.list(10L, 0, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COMMUNITY_POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("공개 게시글의 댓글 목록은 정상적으로 조회된다")
    void list_publishedPost_returnsComments() {
        given(communityPostRepository.findById(10L)).willReturn(Optional.of(publishedPost()));
        given(communityCommentRepository.findByPostIdAndParentIdIsNull(any(), any())).willReturn(Page.empty());

        service.list(10L, 0, 20);

        verify(communityCommentRepository).findByPostIdAndParentIdIsNull(any(), any());
    }

    @Test
    @DisplayName("B-P0-6: 이미 삭제된 댓글을 다시 삭제 요청하면 멱등하게 아무 것도 하지 않는다")
    void delete_alreadyDeleted_isIdempotentNoOp() {
        CommunityComment comment = new CommunityComment(10L, null, 1L, "작성자", "댓글", OffsetDateTime.now());
        comment.markDeleted();
        given(communityCommentRepository.findByIdForUpdate(5L)).willReturn(Optional.of(comment));

        service.delete(1L, 5L);

        verify(communityCommentRepository, never()).saveAndFlush(any());
        verify(communityPostMetricsRepository, never()).decrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("아직 삭제되지 않은 댓글은 정상적으로 tombstone 처리되고 카운터가 감소한다")
    void delete_notYetDeleted_marksDeletedAndDecrements() {
        CommunityComment comment = new CommunityComment(10L, null, 1L, "작성자", "댓글", OffsetDateTime.now());
        given(communityCommentRepository.findByIdForUpdate(5L)).willReturn(Optional.of(comment));
        given(communityCommentRepository.saveAndFlush(comment)).willReturn(comment);

        service.delete(1L, 5L);

        assertThat(comment.isDeleted()).isTrue();
        verify(communityPostMetricsRepository).decrementCommentCount(10L);
    }
}
