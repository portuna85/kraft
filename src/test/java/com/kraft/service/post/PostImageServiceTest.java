package com.kraft.service.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PostImageService} 단위 테스트. 실제 파일 시스템에 쓰기 때문에 매 테스트마다
 * JUnit5의 {@code @TempDir}로 격리된 임시 디렉터리를 사용한다.
 */
class PostImageServiceTest {

    private PostImageService postImageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        postImageService = new PostImageService();
        ReflectionTestUtils.setField(postImageService, "uploadDir", tempDir.toString());
    }

    @Test
    @DisplayName("store: 허용된 확장자의 파일을 저장하고 /images/로 시작하는 공개 URL을 반환한다")
    void store_정상_저장() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", "fake-image".getBytes());

        String url = postImageService.store(file);

        assertThat(url).startsWith("/images/").endsWith(".png");
    }

    @Test
    @DisplayName("store: 파일이 없으면 IllegalArgumentException")
    void store_파일없으면_예외() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "photo.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> postImageService.store(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("업로드할 파일이 없습니다");
    }

    @Test
    @DisplayName("store: 허용되지 않는 확장자면 IllegalArgumentException")
    void store_허용되지_않는_확장자면_예외() {
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "x".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }

    @Test
    @DisplayName("store: 5MB를 초과하면 IllegalArgumentException")
    void store_5MB초과하면_예외() {
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    @DisplayName("store: 확장자가 없으면 IllegalArgumentException")
    void store_확장자없으면_예외() {
        MockMultipartFile file = new MockMultipartFile("file", "noextension", "image/png", "x".getBytes());

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("확장자");
    }
}
