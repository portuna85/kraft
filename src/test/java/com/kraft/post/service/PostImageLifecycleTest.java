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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

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

    private Authentication alice;
    private Authentication bob;

    @BeforeEach
    void setUp() {
        postImageRepository.deleteAll();
        commentRepository.deleteAll();
        postLikeRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();

        saveUser("alice", "alice@example.com", Role.USER);
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
    @DisplayName("F06: 계정별 저장량 한도를 넘으면 파일을 쓰기 전에 거부한다")
    void uploadImage_overQuota_isRejectedBeforeWritingFile() {
        postService.uploadImage(imageFile(), alice);
        // 이미 한도를 다 쓴 상태로 만든다.
        jdbcTemplate.update("UPDATE post_images SET size_bytes = ? WHERE owner_id = "
                + "(SELECT id FROM users WHERE name = 'alice')", PostImageRegistry.MAX_BYTES_PER_USER);
        // 업로드 디렉터리는 이 클래스의 테스트들이 함께 쓰므로 절대 개수가 아니라 증감을 본다.
        int filesBefore = uploadedFileCount();

        assertThatThrownBy(() -> postService.uploadImage(imageFile(), alice))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("저장 공간을 모두 사용했습니다");

        // 거부된 업로드는 디스크에도 대장에도 흔적을 남기지 않는다.
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

    private void saveUser(String name, String email, Role role) {
        userRepository.save(User.builder().name(name).email(email).password("encoded").role(role).build());
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
