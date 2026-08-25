package com.kraft.community.post;

import static org.assertj.core.api.Assertions.assertThat;

import com.kraft.community.user.CommunityUser;
import com.kraft.community.user.CommunityUserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB-MAP-01(docs/improvement.md): {@code CommunityPost.content}에 {@code columnDefinition}이
 * 없으면 Hibernate 기본 매핑은 {@code varchar(255)}다 — 프로덕션 DDL(V15:9, {@code TEXT})과
 * 어긋나지만 prod는 {@code ddl-auto: validate}라 실제 DDL을 그대로 쓰므로 드러나지 않는다.
 * 이 테스트는 기본 스위트(H2 {@code create-drop})가 그 발산을 실제로 잡는지 고정한다 —
 * {@code columnDefinition = "TEXT"}가 없으면 이 테스트는 저장 시점에 데이터 절단 없이
 * 실패하거나(H2는 varchar 길이 초과 시 예외를 던진다) 본문이 잘려 조회된다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
@DisplayName("게시글 본문 길이 제약 테스트")
class CommunityPostContentLengthTest {

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityUserRepository communityUserRepository;

    @Test
    @DisplayName("255자를 넘는 본문도 손실 없이 저장·조회된다")
    void save_contentLongerThan255Chars_roundTripsWithoutTruncation() {
        CommunityUser owner = communityUserRepository.save(new CommunityUser(
                "google", "owner-" + System.nanoTime(), "글쓴이", null, OffsetDateTime.now()));

        String longContent = "가".repeat(5000);
        CommunityPost saved = communityPostRepository.save(new CommunityPost(
                owner.getId(), "글쓴이", "제목", longContent, PostCategory.GENERAL, null,
                OffsetDateTime.now(), OffsetDateTime.now()));
        communityPostRepository.flush();

        CommunityPost reloaded = communityPostRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getContent()).hasSize(5000).isEqualTo(longContent);
    }
}
