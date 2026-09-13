package com.kraft.service.post;

import com.kraft.domain.comment.CommentRepository;
import com.kraft.domain.post.PostImage;
import com.kraft.domain.post.PostImageRepository;
import com.kraft.domain.post.PostImageStatus;
import com.kraft.domain.post.PostLikeRepository;
import com.kraft.domain.post.PostRepository;
import com.kraft.domain.user.Role;
import com.kraft.domain.user.User;
import com.kraft.domain.user.UserRepository;
import com.kraft.web.dto.post.PostSaveRequestDto;
import com.kraft.web.dto.post.PostUpdateRequestDto;
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
        return new MockMultipartFile("file", "photo.png", "image/png", "fake-image".getBytes());
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
