package com.kraft.post.service;

import com.kraft.post.domain.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PostPurgerTest {

    private static final LocalDateTime THRESHOLD = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostService postService;

    private PostPurger purger;

    @BeforeEach
    void setUp() {
        purger = new PostPurger(postRepository, postService);
        ReflectionTestUtils.setField(purger, "retentionDays", 30);
        ReflectionTestUtils.setField(purger, "enabled", true);
        ReflectionTestUtils.setField(purger, "batchSize", 100);
    }

    @Test
    @DisplayName("한 건이 실패해도 나머지를 계속 지우고, 같은 행을 다시 읽지 않도록 커서로 나아간다")
    void purgeDeletedBefore_continuesPastFailureAndAdvancesCursor() {
        given(postRepository.findIdsDeletedBefore(THRESHOLD, 0L, PageRequest.of(0, 100))).willReturn(List.of(1L, 2L, 3L));
        given(postRepository.findIdsDeletedBefore(THRESHOLD, 3L, PageRequest.of(0, 100))).willReturn(List.of());
        given(postService.purge(1L, THRESHOLD)).willReturn(true);
        willThrow(new IllegalStateException("deadlock")).given(postService).purge(2L, THRESHOLD);
        given(postService.purge(3L, THRESHOLD)).willReturn(true);

        int purged = purger.purgeDeletedBefore(THRESHOLD);

        assertThat(purged).isEqualTo(2);
        // 실패한 2번은 이번 실행에서 다시 읽지 않는다 — 다음 주기가 다시 시도한다.
        verify(postRepository).findIdsDeletedBefore(THRESHOLD, 3L, PageRequest.of(0, 100));
    }

    @Test
    @DisplayName("복구되거나 보관 기간 안이라 purge가 false를 돌려준 글은 세지 않는다")
    void purgeDeletedBefore_doesNotCountSkippedPosts() {
        given(postRepository.findIdsDeletedBefore(THRESHOLD, 0L, PageRequest.of(0, 100))).willReturn(List.of(1L));
        given(postRepository.findIdsDeletedBefore(THRESHOLD, 1L, PageRequest.of(0, 100))).willReturn(List.of());
        given(postService.purge(1L, THRESHOLD)).willReturn(false);

        assertThat(purger.purgeDeletedBefore(THRESHOLD)).isZero();
    }

    @Test
    @DisplayName("비활성화하면 아무것도 읽거나 지우지 않는다")
    void purgeDeletedPosts_whenDisabled_doesNothing() {
        ReflectionTestUtils.setField(purger, "enabled", false);

        purger.purgeDeletedPosts();

        verifyNoInteractions(postRepository, postService);
    }

    @Test
    @DisplayName("활성화하면 보관 기간(일)을 뺀 시각을 기준으로 삼는다")
    void purgeDeletedPosts_usesRetentionDaysAsThreshold() {
        given(postRepository.findIdsDeletedBefore(any(), any(), any())).willReturn(List.of());

        LocalDateTime before = LocalDateTime.now().minusDays(30);
        purger.purgeDeletedPosts();
        LocalDateTime after = LocalDateTime.now().minusDays(30);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).findIdsDeletedBefore(captor.capture(), any(), any());
        assertThat(captor.getValue()).isBetween(before, after);
        verify(postService, never()).purge(any(), any());
    }
}
