package com.kraft.community.comment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.kraft.common.error.ApiException;
import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostMetricsRepository;
import com.kraft.community.post.CommunityPostRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
}
