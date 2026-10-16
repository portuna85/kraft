package com.kraft.post.service;

import com.kraft.post.domain.Post;
import com.kraft.post.domain.PostRepository;
import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.support.MariaDbIntegrationTest;
import com.kraft.support.TestAuthentication;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고정 공지 상한(5)이 동시 요청에서도 지켜지는지 실제 InnoDB로 확인한다. H2 순차 테스트는 "개수를 센 뒤 고정한다" 사이에 다른 요청이 끼어드는
 * 경쟁을 재현하지 못한다. 서로 다른 글의 동시 고정, 고정과 복구, 고정과 숨김 해제를 겹쳐도 활성 고정 수가 상한을 넘지 않아야 한다.
 * Docker가 없으면 건너뛴다.
 */
class PostPinLimitConcurrencyMariaDbTest extends MariaDbIntegrationTest {

    private static final int LIMIT = PostQueryService.PINNED_LIMIT;

    @Autowired
    private PostModerationService moderationService;

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    private User author;
    private Authentication authorAuth;

    @BeforeEach
    void setUp() {
        author = userRepository.save(User.builder()
                .name("pin-author").email("pin-author@example.com").password("encoded").role(Role.USER).build());
        authorAuth = TestAuthentication.of(author);
    }

    private Post newPost() {
        return postRepository.save(Post.builder().title("제목").content("내용").user(author).build());
    }

    private void pinCount(int count) {
        for (int i = 0; i < count; i++) {
            moderationService.pin(newPost().getId(), LocalDateTime.now().plusDays(7));
        }
    }

    private long activePinned() {
        return postRepository.countPinned(LocalDateTime.now());
    }

    /** 모든 작업이 배리어에서 함께 출발하게 하고 각자의 결과(성공이면 null, 실패면 예외)를 모아 돌려준다. */
    private List<Throwable> runTogether(List<Runnable> tasks) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                Callable<Throwable> call = () -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    try {
                        task.run();
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };
                futures.add(executor.submit(call));
            }
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("이미 4개가 고정된 상태에서 서로 다른 두 글을 동시에 고정하면 하나만 성공하고 활성 고정은 5개다")
    void concurrentPinOfDifferentPosts_neverExceedsLimit() throws Exception {
        pinCount(LIMIT - 1);
        Post first = newPost();
        Post second = newPost();
        LocalDateTime until = LocalDateTime.now().plusDays(7);

        List<Throwable> outcomes = runTogether(List.of(
                () -> moderationService.pin(first.getId(), until),
                () -> moderationService.pin(second.getId(), until)));

        assertThat(outcomes.stream().filter(Objects::isNull)).hasSize(1);
        assertThat(outcomes.stream().filter(Objects::nonNull))
                .singleElement().isInstanceOf(BusinessValidationException.class);
        assertThat(activePinned()).isEqualTo(LIMIT);
    }

    @Test
    @DisplayName("고정과 복구가 겹쳐도 활성 고정은 상한을 넘지 않고 복구는 성공한다")
    void concurrentPinAndRestore_neverExceedsLimit() throws Exception {
        pinCount(LIMIT - 1);
        Post deletedButPinned = newPost();
        moderationService.pin(deletedButPinned.getId(), LocalDateTime.now().plusDays(7));
        postService.delete(deletedButPinned.getId(), authorAuth);
        Post another = newPost();

        List<Throwable> outcomes = runTogether(List.of(
                () -> moderationService.restore(deletedButPinned.getId()),
                () -> moderationService.pin(another.getId(), LocalDateTime.now().plusDays(7))));

        assertThat(activePinned()).isLessThanOrEqualTo(LIMIT);
        // 복구는 상한과 무관하게 성공해야 한다. 고정은 순서에 따라 성공하거나 상한으로 거절된다.
        assertThat(postRepository.findById(deletedButPinned.getId()).orElseThrow().isDeleted()).isFalse();
        outcomes.stream().filter(Objects::nonNull)
                .forEach(t -> assertThat(t).isInstanceOf(BusinessValidationException.class));
    }

    @Test
    @DisplayName("고정과 숨김 해제가 겹쳐도 활성 고정은 상한을 넘지 않고 숨김 해제는 성공한다")
    void concurrentPinAndUnblind_neverExceedsLimit() throws Exception {
        pinCount(LIMIT - 1);
        Post blindedButPinned = newPost();
        moderationService.pin(blindedButPinned.getId(), LocalDateTime.now().plusDays(7));
        moderationService.blindPost(blindedButPinned.getId());
        Post another = newPost();

        List<Throwable> outcomes = runTogether(List.of(
                () -> moderationService.unblindPost(blindedButPinned.getId()),
                () -> moderationService.pin(another.getId(), LocalDateTime.now().plusDays(7))));

        assertThat(activePinned()).isLessThanOrEqualTo(LIMIT);
        assertThat(postRepository.findById(blindedButPinned.getId()).orElseThrow().getBlindedAt()).isNull();
        outcomes.stream().filter(Objects::nonNull)
                .forEach(t -> assertThat(t).isInstanceOf(BusinessValidationException.class));
    }

    @Test
    @DisplayName("이미 5개가 고정 중일 때 고정이 남은 삭제 글을 복구하면 복구는 되지만 그 글의 고정은 풀린다")
    void restoreWhenAtLimit_restoresAndDropsItsPin() {
        pinCount(LIMIT - 1);
        Post deletedButPinned = newPost();
        moderationService.pin(deletedButPinned.getId(), LocalDateTime.now().plusDays(7));
        postService.delete(deletedButPinned.getId(), authorAuth);
        moderationService.pin(newPost().getId(), LocalDateTime.now().plusDays(7));
        assertThat(activePinned()).isEqualTo(LIMIT);

        moderationService.restore(deletedButPinned.getId());

        Post restored = postRepository.findById(deletedButPinned.getId()).orElseThrow();
        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getPinnedUntil()).isNull();
        assertThat(activePinned()).isEqualTo(LIMIT);
    }
}
