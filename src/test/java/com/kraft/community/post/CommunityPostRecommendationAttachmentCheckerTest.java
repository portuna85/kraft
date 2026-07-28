package com.kraft.community.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("추천 세트 첨부 여부 확인자 단위 테스트")
class CommunityPostRecommendationAttachmentCheckerTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    private CommunityPostRecommendationAttachmentChecker checker;

    @BeforeEach
    void setUp() {
        checker = new CommunityPostRecommendationAttachmentChecker(communityPostRepository);
    }

    @Test
    @DisplayName("게시글에 첨부된 세트면 true를 반환한다")
    void isAttachedToPost_whenAttached_returnsTrue() {
        given(communityPostRepository.existsByRecommendationSetId(5L)).willReturn(true);

        assertThat(checker.isAttachedToPost(5L)).isTrue();
    }

    @Test
    @DisplayName("첨부된 게시글이 없으면 false를 반환한다")
    void isAttachedToPost_whenNotAttached_returnsFalse() {
        given(communityPostRepository.existsByRecommendationSetId(5L)).willReturn(false);

        assertThat(checker.isAttachedToPost(5L)).isFalse();
    }
}
