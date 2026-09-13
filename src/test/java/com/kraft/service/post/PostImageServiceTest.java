package com.kraft.service.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.kraft.support.TestImages;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PostImageService} 단위 테스트. 실제 파일 시스템에 쓰기 때문에 매 테스트마다
 * JUnit5의 {@code @TempDir}로 격리된 임시 디렉터리를 사용한다.
 */
class PostImageServiceTest {

    private PostImageService postImageService;
    private Path uploadDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        uploadDir = tempDir;
        postImageService = new PostImageService();
        ReflectionTestUtils.setField(postImageService, "uploadDir", tempDir.toString());
    }

    @Test
    @DisplayName("store: 허용된 확장자의 파일을 저장하고 /images/로 시작하는 공개 URL을 반환한다")
    void store_withAllowedExtension_savesFileAndReturnsPublicUrl() {
        MockMultipartFile file = TestImages.pngFile("photo.png");

        String url = postImageService.store(file);

        assertThat(url).startsWith("/images/").endsWith(".png");
    }

    @Test
    @DisplayName("store: 파일이 없으면 IllegalArgumentException")
    void store_whenFileIsEmpty_throwsIllegalArgumentException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> postImageService.store(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드할 파일이 없습니다");
    }

    @Test
    @DisplayName("store: 허용되지 않는 확장자면 IllegalArgumentException")
    void store_withDisallowedExtension_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }

    @Test
    @DisplayName("store: 대문자 확장자(IMG_0001.JPG)도 허용하고 소문자 확장자로 저장한다")
    void store_withUppercaseExtension_savesWithLowercaseExtension() {
        MockMultipartFile file = TestImages.jpegFile("IMG_0001.JPG");

        String url = postImageService.store(file);

        assertThat(url).endsWith(".jpg");
        assertThat(Files.exists(uploadDir.resolve(url.substring("/images/".length())))).isTrue();
    }

    @Test
    @DisplayName("store: 아이폰 HEIC는 확장자만으로 거부하고 해결 방법을 안내한다")
    void store_withHeicExtension_throwsWithGuidanceMessage() {
        MockMultipartFile file = new MockMultipartFile("file", "IMG_0001.HEIC", "image/heic", "x".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HEIC")
                .hasMessageContaining("높은 호환성");
    }

    @Test
    @DisplayName("store: 확장자를 .jpg로 바꾼 HEIC도 파일 내용(ftyp 브랜드)으로 걸러낸다")
    void store_withHeicContentRenamedToJpg_throwsWithGuidanceMessage() {
        MockMultipartFile file = new MockMultipartFile("file", "renamed.jpg", "image/jpeg", heifHeader("heic"));

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HEIC");
    }

    @Test
    @DisplayName("store: ftyp 박스지만 HEIF 브랜드가 아니면(예: mp4) 확장자 검사로 넘어간다")
    void store_withNonHeifFtypBrand_fallsBackToExtensionCheck() {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", heifHeader("isom"));

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }

    @Test
    @DisplayName("store: 헤더보다 짧은 파일은 IOException이 아니라 형식 오류로 거부한다")
    void store_withFileShorterThanHeader_isRejectedAsInvalidImage() {
        MockMultipartFile file = new MockMultipartFile("file", "tiny.png", "image/png", "ab".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지 파일이 아니거나");
    }

    @Test
    @DisplayName("store: 확장자만 .png인 일반 텍스트는 내용 검사에서 거부한다")
    void store_withTextContentNamedPng_isRejected() {
        // 예전에는 확장자만 봤기 때문에 이 파일이 그대로 저장됐다(개선 보고서 F06).
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-an-image.png", "text/plain", "이건 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미지 파일이 아니거나");
    }

    @Test
    @DisplayName("store: 내용은 PNG인데 확장자가 .jpg면 형식이 다르다고 거부한다")
    void store_whenContentAndExtensionDisagree_isRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "disguised.jpg", "image/jpeg", TestImages.pngBytes(1, 1));

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("확장자와 실제 형식이 다릅니다");
    }

    @Test
    @DisplayName("store: 5MB를 초과하면 IllegalArgumentException")
    void store_whenFileSizeExceedsLimit_throwsIllegalArgumentException() {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    @DisplayName("store: 확장자가 없으면 IllegalArgumentException")
    void store_whenFileHasNoExtension_throwsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/png", "x".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("확장자");
    }

    @Test
    @DisplayName("deleteIfExists: store()가 만든 URL로 실제 파일을 지운다")
    void deleteIfExists_withStoredUrl_deletesActualFile() {
        MockMultipartFile file = TestImages.pngFile("photo.png");
        String url = postImageService.store(file);
        Path saved = uploadDir.resolve(url.substring("/images/".length()));
        assertThat(Files.exists(saved)).isTrue();

        postImageService.deleteIfExists(url);

        assertThat(Files.exists(saved)).isFalse();
    }

    @Test
    @DisplayName("deleteIfExists: null이면 아무 일도 하지 않는다")
    void deleteIfExists_whenUrlIsNull_doesNothing() {
        postImageService.deleteIfExists(null);
    }

    @Test
    @DisplayName("deleteIfExists: /images/ 접두어가 아니면 무시한다")
    void deleteIfExists_whenUrlDoesNotStartWithImagesPrefix_doesNothing() {
        postImageService.deleteIfExists("https://external.example.com/photo.png");
    }

    @Test
    @DisplayName("deleteIfExists: 존재하지 않는 파일이면 예외 없이 무시한다")
    void deleteIfExists_whenFileDoesNotExist_doesNothingWithoutException() {
        postImageService.deleteIfExists("/images/never-existed.png");
    }

    @Test
    @DisplayName("deleteIfExists: 경로 조작(../)으로 업로드 디렉터리 밖을 가리키면 지우지 않는다")
    void deleteIfExists_withPathTraversal_doesNotDeleteFileOutsideDirectory() throws IOException {
        Path outside = uploadDir.getParent().resolve("outside-secret.png");
        Files.writeString(outside, "secret");

        postImageService.deleteIfExists("/images/../outside-secret.png");

        assertThat(Files.exists(outside)).isTrue();
        Files.deleteIfExists(outside);
    }

    /** ISO base media file format 헤더: [size(4)][ftyp][brand(4)]. */
    private byte[] heifHeader(String brand) {
        byte[] header = new byte[24];
        System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, header, 4, 4);
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
        return header;
    }
}
