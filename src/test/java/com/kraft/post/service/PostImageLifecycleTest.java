package com.kraft.post.service;

import com.kraft.comment.domain.CommentRepository;
import com.kraft.post.domain.PostImage;
import com.kraft.post.domain.PostImageRepository;
import com.kraft.post.domain.PostImageStatus;
import com.kraft.post.domain.PostLikeRepository;
import com.kraft.post.domain.PostRepository;
import com.kraft.post.dto.PostSaveRequestDto;
import com.kraft.post.dto.PostUpdateRequestDto;
import com.kraft.support.TestImages;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이미지 소유권(개선 보고서 F01)과 파일 생명주기(F05)를 실제 DB·실제 파일로 검증한다.
 * <p>
 * 기존 {@code PostServiceTest}는 mock 리포지토리를 쓰기 때문에 커밋·롤백과 디스크 상태를
 * 관찰하지 못한다 — 바로 그 공백에서 두 결함이 살아 있었다. 여기서는 트랜잭션을 실제로
 * 커밋·롤백시키고 파일이 남았는지 사라졌는지를 직접 확인한다.
 */
@SpringBootTest
class PostImageLifecycleTest {

    private static Path uploadDir;

    @DynamicPropertySource
    static void uploadDir(DynamicPropertyRegistry registry) throws IOException {
        uploadDir = Files.createTempDirectory("kraft-image-lifecycle");
        registry.add("app.upload.dir", () -> uploadDir.toString());
    }

    @Autowired
    private PostService postService;

    @Autowired
    private PostImageCleaner postImageCleaner;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private PostImageRegistry postImageRegistry;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 겹친 트랜잭션을 만들기 위한 별도 템플릿. 기본 전파(REQUIRED)로는 바깥 트랜잭션에
     * 참여해 같은 영속성 컨텍스트를 쓰므로 "다른 트랜잭션이 먼저 커밋했다"를 재현할 수 없다.
     */
    private TransactionTemplate requiresNew;

    private Authentication alice;
    private Authentication bob;
    private User aliceUser;

    @BeforeEach
    void setUp() {
        requiresNew = new TransactionTemplate(transactionTemplate.getTransactionManager());
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        postImageRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        aliceUser = saveUser("alice", "alice@example.com", Role.USER);
        saveUser("bob", "bob@example.com", Role.USER);
        saveUser("guest", "guest@example.com", Role.GUEST);

        alice = authOf("alice@example.com", Role.USER);
        bob = authOf("bob@example.com", Role.USER);
    }

    @Test
    @DisplayName("F01: 남이 올린 이미지를 자기 게시글에 붙일 수 없다")
    void save_withAnotherUsersImage_isRejected() {
        String url = postService.uploadImage(imageFile(), alice);

        assertThatThrownBy(() -> postService.save(bob, new PostSaveRequestDto("제목", "내용", url, null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("직접 업로드한 이미지");

        assertThat(fileOf(url)).exists();
        assertThat(postRepository.count()).isZero();
    }

    @Test
    @DisplayName("F01: 남의 이미지를 붙인 글을 지워서 그 파일을 없앨 수 없다 — 재현했던 경로 전체")
    void delete_cannotRemoveAnotherUsersImageFile() {
        String url = postService.uploadImage(imageFile(), alice);
        Long alicePost = postService.save(alice, new PostSaveRequestDto("앨리스 글", "내용", url, null));

        // 밥이 같은 URL로 자기 글을 만들려는 시도 자체가 막힌다. 예전에는 이 글이 만들어졌고,
        // 밥이 그 글을 지우면 앨리스의 파일이 사라졌다.
        assertThatThrownBy(() -> postService.save(bob, new PostSaveRequestDto("밥 글", "내용", url, null)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(fileOf(url)).exists();
        assertThat(postRepository.findById(alicePost)).isPresent();
    }

    @Test
    @DisplayName("F01: 이미 다른 게시글이 쓰는 이미지는 재사용할 수 없다")
    void save_withImageAlreadyAttachedToAnotherPost_isRejected() {
        String url = postService.uploadImage(imageFile(), alice);
        postService.save(alice, new PostSaveRequestDto("첫 글", "내용", url, null));

        assertThatThrownBy(() -> postService.save(alice, new PostSaveRequestDto("둘째 글", "내용", url, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 다른 게시글");
    }

    @Test
    @DisplayName("F01: 업로드 기록이 없는 이미지 주소는 거부한다")
    void save_withUnknownImageUrl_isRejected() {
        assertThatThrownBy(() -> postService.save(alice,
                new PostSaveRequestDto("제목", "내용", "/images/never-uploaded.png", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드 기록이 없는");
    }

    @Test
    @DisplayName("F06: 이메일 인증 전(GUEST)이면 업로드할 수 없다")
    void uploadImage_whenGuest_isRejected() {
        assertThatThrownBy(() -> postService.uploadImage(imageFile(), authOf("guest@example.com", Role.GUEST)))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(postImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("F06: 확장자만 이미지인 파일은 내용 검사에서 거부하고 대장에도 남기지 않는다")
    void uploadImage_withTextContentNamedPng_isRejected() {
        var notAnImage = new MockMultipartFile("file", "not-an-image.png", "image/png",
                "이건 그냥 텍스트입니다".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> postService.uploadImage(notAnImage, alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지 파일이 아니거나");

        assertThat(postImageRepository.count()).isZero();
    }

    @Test
    @DisplayName("F05/F06: 계정별 저장량 한도를 넘으면 대장 등록을 거부하고, 이미 쓴 파일도 곧바로 지운다")
    void uploadImage_overQuota_leavesNoOrphanFile() {
        postService.uploadImage(imageFile(), alice);
        // 이미 한도를 다 쓴 상태로 만든다.
        jdbcTemplate.update("UPDATE post_images SET size_bytes = ? WHERE owner_id = "
                + "(SELECT id FROM users WHERE name = 'alice')", PostImageRegistry.MAX_BYTES_PER_USER);
        // 업로드 디렉터리는 이 클래스의 테스트들이 함께 쓰므로 절대 개수가 아니라 증감을 본다.
        int filesBefore = uploadedFileCount();

        // 용량 검사와 등록이 한 트랜잭션에서 원자적으로 일어나므로, 파일은 검사보다 먼저
        // 디스크에 쓰이지만 등록이 실패하면 곧바로 보상 삭제된다 — 대장 없는 파일이 남지
        // 않는다(개선 보고서 "파일 저장 성공 후 DB 롤백 시 대장 없는 파일").
        assertThatThrownBy(() -> postService.uploadImage(imageFile(), alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("저장 공간을 모두 사용했습니다");

        assertThat(postImageRepository.count()).isEqualTo(1);
        assertThat(uploadedFileCount()).isEqualTo(filesBefore);
    }

    private int uploadedFileCount() {
        java.io.File[] files = uploadDir.toFile().listFiles();
        return files == null ? 0 : files.length;
    }

    @Test
    @DisplayName("F06: 다른 사람의 저장량은 내 한도에 영향을 주지 않는다")
    void uploadImage_quotaIsPerUser() {
        postService.uploadImage(imageFile(), alice);
        jdbcTemplate.update("UPDATE post_images SET size_bytes = ? WHERE owner_id = "
                + "(SELECT id FROM users WHERE name = 'alice')", PostImageRegistry.MAX_BYTES_PER_USER);

        // 밥은 아직 한 번도 올리지 않았으므로 정상 동작해야 한다.
        assertThat(postService.uploadImage(imageFile(), bob)).startsWith("/images/");
    }

    @Test
    @DisplayName("F05: 게시글 삭제가 커밋되면 붙어 있던 이미지 파일도 정리된다")
    void delete_afterCommit_removesImageFile() {
        String url = postService.uploadImage(imageFile(), alice);
        Long postId = postService.save(alice, new PostSaveRequestDto("제목", "내용", url, null));

        postService.delete(postId, alice);

        assertThat(fileOf(url)).doesNotExist();
        assertThat(postImageRepository.findByFileName(fileNameOf(url))).isEmpty();
    }

    @Test
    @DisplayName("F05: 이미지 교체 트랜잭션이 롤백되면 기존 이미지 파일이 그대로 남는다")
    void update_whenTransactionRollsBack_keepsOldImageFile() {
        String oldUrl = postService.uploadImage(imageFile(), alice);
        Long postId = postService.save(alice, new PostSaveRequestDto("제목", "내용", oldUrl, null));
        String newUrl = postService.uploadImage(imageFile(), alice);

        // 이미지를 교체한 뒤 같은 트랜잭션을 롤백시킨다. 예전에는 update()가 커밋 전에 파일을
        // 지웠기 때문에, DB는 기존 이미지를 가리키는 상태로 되돌아오는데 파일은 이미 없었다.
        transactionTemplate.executeWithoutResult(status -> {
            postService.update(postId, new PostUpdateRequestDto("제목", "내용", newUrl, null, null), alice);
            status.setRollbackOnly();
        });

        assertThat(postRepository.findById(postId).orElseThrow().getPicture()).isEqualTo(oldUrl);
        assertThat(fileOf(oldUrl)).exists();
        assertThat(imageStatusOf(oldUrl)).isEqualTo(PostImageStatus.ATTACHED);
    }

    @Test
    @DisplayName("F05: 이미지 교체가 커밋되면 기존 파일만 정리되고 새 파일은 남는다")
    void update_afterCommit_removesOnlyOldImageFile() {
        String oldUrl = postService.uploadImage(imageFile(), alice);
        Long postId = postService.save(alice, new PostSaveRequestDto("제목", "내용", oldUrl, null));
        String newUrl = postService.uploadImage(imageFile(), alice);

        postService.update(postId, new PostUpdateRequestDto("제목", "내용", newUrl, null, null), alice);

        assertThat(fileOf(oldUrl)).doesNotExist();
        assertThat(fileOf(newUrl)).exists();
        assertThat(imageStatusOf(newUrl)).isEqualTo(PostImageStatus.ATTACHED);
    }

    @Test
    @DisplayName("F05: 커밋 직후 정리가 실행되지 못했어도 예약이 DB에 남아 다음 정리가 마저 치운다")
    void cleanPendingDeletions_picksUpReservationsLeftBehind() {
        String url = postService.uploadImage(imageFile(), alice);

        // 커밋은 됐는데 그 직후 프로세스가 죽어 파일 삭제가 실행되지 못한 상태를 만든다.
        jdbcTemplate.update("UPDATE post_images SET status = 'PENDING_DELETE' WHERE file_name = ?",
                fileNameOf(url));
        assertThat(fileOf(url)).exists();

        assertThat(postImageCleaner.cleanPendingDeletions()).isEqualTo(1);

        assertThat(fileOf(url)).doesNotExist();
        assertThat(postImageRepository.findByFileName(fileNameOf(url))).isEmpty();
        // 두 번째 실행은 할 일이 없다.
        assertThat(postImageCleaner.cleanPendingDeletions()).isZero();
    }

    @Test
    @DisplayName("업로드만 하고 글을 저장하지 않은 파일은 만료 후 정리된다")
    void cleanExpiredOrphans_removesUnattachedUploads() {
        String fresh = postService.uploadImage(imageFile(), alice);
        String stale = postService.uploadImage(imageFile(), alice);
        backdate(stale, LocalDateTime.now().minusHours(25));

        int deleted = postImageCleaner.cleanExpiredOrphans();

        assertThat(deleted).isEqualTo(1);
        assertThat(fileOf(stale)).doesNotExist();
        // 방금 올린 파일은 아직 글을 쓰는 중일 수 있으므로 건드리지 않는다.
        assertThat(fileOf(fresh)).exists();
    }

    @Test
    @DisplayName("F03/F04: 같은 이미지를 서로 다른 두 게시글에 거의 동시에 붙이면 나중에 커밋한 쪽이 충돌로 실패한다")
    void attach_concurrentAttachToDifferentPosts_conflictsOnOptimisticLock() {
        String url = postService.uploadImage(imageFile(), alice);
        Long postAId = postService.save(alice, new PostSaveRequestDto("A", "내용", null, null));
        Long postBId = postService.save(alice, new PostSaveRequestDto("B", "내용", null, null));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(outer -> {
            // 바깥 트랜잭션이 이미지를 읽어(version=0) postA에 붙인다. 아직 커밋 전이다.
            postImageRegistry.attach(url, aliceUser, postRepository.findById(postAId).orElseThrow());

            // 그 사이 독립된 트랜잭션이 같은 이미지를 postB에 붙이고 먼저 커밋한다.
            requiresNew.executeWithoutResult(inner ->
                    postImageRegistry.attach(url, aliceUser, postRepository.findById(postBId).orElseThrow()));

            // 바깥 트랜잭션이 이제야 커밋을 시도한다 — 손에 쥔 버전이 이미 낡았으므로 실패해야
            // 한다. 예전에는 잠금이 없어 이 시나리오가 조용히 성공하고 postA가 이겼다.
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(imageStatusOf(url)).isEqualTo(PostImageStatus.ATTACHED);
        assertThat(postRepository.findById(postBId).orElseThrow().getPicture()).isNull();
    }

    /**
     * B03: {@code validateQuotaAndRegister}가 이제 계정의 {@code PostImage} 행이 아니라 User
     * 행 자체를 잠근다(B07의 {@code findByIdForUpdate}, NOWAIT 없이 블로킹). 그래서 더는 같은
     * 스레드에서 바깥 트랜잭션이 커밋되지 않은 채 안쪽 트랜잭션을 동기 호출하는 방식(다른 테스트의
     * 낙관적 잠금 검증 패턴)을 쓸 수 없다 — 안쪽이 바깥의 잠금을 기다리며 그대로 멈춘다(자기
     * 교착). 실제로 동시에 도는 두 스레드로 검증한다.
     */
    @Test
    @DisplayName("B03: 같은 계정의 동시 업로드는 용량 검사·등록이 직렬화되어 한도를 넘지 않는다")
    void validateQuotaAndRegister_concurrentUploadsForSameOwner_areSerialized() throws InterruptedException {
        // 기존 행이 하나 있는 계정에서도 직렬화되는지 확인한다.
        postService.uploadImage(imageFile(), alice);
        assertConcurrentRegistrationsAreSerialized();
    }

    /**
     * B03: 예전에는 잠글 기존 {@code PostImage} 행이 없으면(첫 업로드) 동시 경쟁을 막지 못했다.
     * User 행은 항상 존재하므로 이미지가 하나도 없는 계정의 첫 업로드 두 건도 직렬화되어야 한다.
     */
    @Test
    @DisplayName("B03: 이미지가 하나도 없는 계정의 첫 업로드 두 건도 직렬화된다")
    void validateQuotaAndRegister_firstUploadsForOwnerWithNoImages_areSerialized() throws InterruptedException {
        assertConcurrentRegistrationsAreSerialized();
    }

    private void assertConcurrentRegistrationsAreSerialized() throws InterruptedException {
        // 두 번 다 등록되면 한도(50MiB)를 넘는 크기로 골라, 직렬화가 깨지면(둘 다 같은 used=0을
        // 보고 통과) 검증이 실제로 실패하게 만든다.
        long eachSize = PostImageRegistry.MAX_BYTES_PER_USER / 2 + 1024;
        List<Boolean> results = new java.util.concurrent.CopyOnWriteArrayList<>();

        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(2);
        Runnable attempt = () -> {
            ready.countDown();
            try {
                ready.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                postImageRegistry.validateQuotaAndRegister(
                        "/images/" + java.util.UUID.randomUUID() + ".png", aliceUser, eachSize);
                results.add(true);
            } catch (IllegalArgumentException e) {
                results.add(false);
            }
        };
        Thread t1 = new Thread(attempt);
        Thread t2 = new Thread(attempt);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        // 직렬화되었다면 두 번째 호출이 첫 번째가 커밋한 사용량을 보고 한도 초과로 거부된다.
        assertThat(results).containsExactlyInAnyOrder(true, false);
    }

    @Test
    @DisplayName("F06: 커밋 후 정리는 이번 요청이 표시한 이미지만 치우고 다른 삭제 대기 이미지는 건드리지 않는다")
    void update_cleanUpAfterCommit_touchesOnlyThisRequestsImage() {
        String oldUrl = postService.uploadImage(imageFile(), alice);
        Long postId = postService.save(alice, new PostSaveRequestDto("제목", "내용", oldUrl, null));
        String newUrl = postService.uploadImage(imageFile(), alice);

        // 이 요청과 무관하게 이미 삭제 대기 중인 이미지가 있다고 가정한다 — 시스템 전체의
        // 밀린 삭제 대기열을 흉내 낸다.
        String unrelatedUrl = postService.uploadImage(imageFile(), alice);
        jdbcTemplate.update("UPDATE post_images SET status = 'PENDING_DELETE' WHERE file_name = ?",
                fileNameOf(unrelatedUrl));

        postService.update(postId, new PostUpdateRequestDto("제목", "내용", newUrl, null, null), alice);

        // 이번 요청이 표시한 것만 곧바로 지워진다.
        assertThat(fileOf(oldUrl)).doesNotExist();
        assertThat(postImageRepository.findByFileName(fileNameOf(oldUrl))).isEmpty();

        // 무관한 삭제 대기 이미지는 예약 작업이 돌기 전까지 그대로 남는다.
        assertThat(fileOf(unrelatedUrl)).exists();
        assertThat(imageStatusOf(unrelatedUrl)).isEqualTo(PostImageStatus.PENDING_DELETE);
    }

    @Test
    @DisplayName("B02: 등록까지 끝난 뒤 트랜잭션이 커밋되지 않으면 저장한 파일도 함께 없어진다")
    void uploadImage_whenTransactionDoesNotCommit_deletesTheStoredFile() {
        var urlRef = new java.util.concurrent.atomic.AtomicReference<String>();

        // uploadImage() 안에서는 파일 저장·대장 등록 모두 정상 끝난다. 그런데도 바깥
        // 트랜잭션이 커밋되지 않으면(여기서는 강제 rollback-only로 흉내 낸다), 대장 행은 DB
        // 롤백으로 자연히 사라지지만 이미 디스크에 쓴 파일은 그렇지 않다 — OnRollback으로
        // 등록한 보상이 이것까지 지워야 한다.
        transactionTemplate.executeWithoutResult(status -> {
            urlRef.set(postService.uploadImage(imageFile(), alice));
            status.setRollbackOnly();
        });

        String url = urlRef.get();
        assertThat(fileOf(url)).doesNotExist();
        assertThat(postImageRepository.findByFileName(fileNameOf(url))).isEmpty();
    }

    @Test
    @DisplayName("B01: 삭제가 예약된 이미지는 다시 연결할 수 없다")
    void attach_toImageMarkedForDeletion_isRejected() {
        String url = postService.uploadImage(imageFile(), alice);
        Long postId = postService.save(alice, new PostSaveRequestDto("원본", "내용", null, null));
        jdbcTemplate.update("UPDATE post_images SET status = 'PENDING_DELETE', post_id = NULL WHERE file_name = ?",
                fileNameOf(url));

        assertThatThrownBy(() -> postImageRegistry.attach(url, aliceUser, postRepository.findById(postId).orElseThrow()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("삭제 예정");

        assertThat(imageStatusOf(url)).isEqualTo(PostImageStatus.PENDING_DELETE);
    }

    @Test
    @DisplayName("B01: 정리 작업이 대상을 고른 뒤 다른 트랜잭션이 먼저 연결하면 파일을 지우지 않고 건너뛴다")
    void cleanExpiredOrphans_skipsImageAttachedBetweenSelectAndClaim() {
        String url = postService.uploadImage(imageFile(), alice);
        backdate(url, LocalDateTime.now().minusHours(25));
        Long postId = postService.save(alice, new PostSaveRequestDto("제목", "내용", null, null));
        Long imageId = postImageRepository.findByFileName(fileNameOf(url)).orElseThrow().getId();
        LocalDateTime threshold = LocalDateTime.now().minus(PostImageCleaner.ORPHAN_TTL);

        // 정리 작업이 만료된 ORPHAN 대상을 조회한 시점을 흉내 낸다 — 아직 파일을 지우기 전
        // 조건부 선점(claim)을 하지 않았다. 그 사이 다른 트랜잭션이 먼저 이 이미지를 게시글에
        // 연결하고 커밋한다.
        requiresNew.executeWithoutResult(inner ->
                postImageRegistry.attach(url, aliceUser, postRepository.findById(postId).orElseThrow()));

        // 정리 작업이 이제야 파일을 지우기 전 조건부 선점을 시도한다 — 이미 ATTACHED로 바뀌어
        // ORPHAN 조건에 맞지 않으므로 0행이어야 하고, 파일을 지우면 안 된다.
        int claimed = transactionTemplate.execute(status ->
                postImageRepository.claimExpiredOrphanForDeletion(imageId, threshold));

        assertThat(claimed).isZero();
        assertThat(fileOf(url)).exists();
        assertThat(imageStatusOf(url)).isEqualTo(PostImageStatus.ATTACHED);
    }

    private User saveUser(String name, String email, Role role) {
        return userRepository.save(User.builder().name(name).email(email).password("encoded").role(role).build());
    }

    private static Authentication authOf(String email, Role role) {
        return new UsernamePasswordAuthenticationToken(email, null,
                java.util.List.of(new SimpleGrantedAuthority(role.getKey())));
    }

    private static MockMultipartFile imageFile() {
        return TestImages.pngFile("photo.png");
    }

    private static String fileNameOf(String url) {
        return PostImageService.fileNameOf(url);
    }

    private Path fileOf(String url) {
        return uploadDir.resolve(fileNameOf(url));
    }

    private PostImageStatus imageStatusOf(String url) {
        return postImageRepository.findByFileName(fileNameOf(url))
                .map(PostImage::getStatus)
                .orElse(null);
    }

    private void backdate(String url, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE post_images SET created_at = ? WHERE file_name = ?",
                createdAt, fileNameOf(url));
    }
}
