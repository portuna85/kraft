package com.kraft.community.comment;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.community.post.CommunityPost;
import com.kraft.community.post.CommunityPostRepository;
import com.kraft.community.post.PostCategory;
import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * M-05 회귀 테스트: {@code findByPostIdAndParentIdInOrderByCreatedAtAscIdAsc}가 실제로
 * 정렬절을 적용하는지 검증한다 — 일부러 id 역순(최신 것부터)으로 저장해, 정렬절이
 * 없었다면 저장 순서 그대로 나올 수도 있는 상황을 만든다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("댓글 리포지토리 정렬 테스트")
class CommunityCommentRepositoryTest {

    @Autowired
    private CommunityCommentRepository communityCommentRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    private Long postId;

    @BeforeEach
    void setUp() {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));
        CommunityPost post = communityPostRepository.save(new CommunityPost(
                owner.getId(), "글쓴이", "제목", "내용", PostCategory.GENERAL, null,
                OffsetDateTime.now(), OffsetDateTime.now()));
        postId = post.getId();
    }

    @Test
    @DisplayName("답글은 저장 순서와 무관하게 createdAt,id 오름차순으로 반환된다")
    void findByPostIdAndParentIdIn_ordersByCreatedAtThenId() {
        CommunityComment parent = communityCommentRepository.save(
                new CommunityComment(postId, null, null, "글쓴이", "상위 댓글", OffsetDateTime.now()));

        OffsetDateTime base = OffsetDateTime.now();
        // 일부러 늦게 작성된 답글(reply3)을 먼저 저장해, 정렬절이 없으면 삽입 순서(reply3,
        // reply1, reply2)로 보일 수 있는 상황을 만든다.
        CommunityComment reply3 = communityCommentRepository.save(new CommunityComment(
                postId, parent.getId(), null, "글쓴이", "세 번째 답글", base.plusMinutes(2)));
        CommunityComment reply1 = communityCommentRepository.save(new CommunityComment(
                postId, parent.getId(), null, "글쓴이", "첫 번째 답글", base));
        CommunityComment reply2 = communityCommentRepository.save(new CommunityComment(
                postId, parent.getId(), null, "글쓴이", "두 번째 답글", base.plusMinutes(1)));

        List<CommunityComment> replies = communityCommentRepository
                .findByPostIdAndParentIdInOrderByCreatedAtAscIdAsc(postId, List.of(parent.getId()));

        assertThat(replies).extracting(CommunityComment::getId)
                .containsExactly(reply1.getId(), reply2.getId(), reply3.getId());
    }
}
