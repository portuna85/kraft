package com.kraft.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PostImageService}의 WEBP 픽셀 수 검사(F04). 표준 JDK ImageIO에는 WEBP 디코더가
 * 없어({@code ImageIO.getImageReaders}가 빈 목록을 돌려줌) 일반 경로를 못 타므로, 컨테이너
 * 헤더를 직접 읽는 별도 경로를 검증한다.
 * <p>
 * 세 하위 형식(VP8X 확장, VP8 단순 손실, VP8L 무손실) 모두 WebP 컨테이너 명세의 고정된
 * 바이트 배치를 손으로 만들어 확인한다 — 실제 인코더 없이도 명세와 대조해 정확성을 검증할
 * 수 있다.
 */
class PostImageServiceWebpTest {

    private PostImageService postImageService;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        postImageService = new PostImageService();
        ReflectionTestUtils.setField(postImageService, "uploadDir", tempDir.toString());
    }

    private static byte[] le32(int v) {
        return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
    }

    private static byte[] riff(String subFourCC, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(4 + 8 + payload.length)); // "WEBP" + 청크 헤더(8) + payload
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(subFourCC.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(payload.length));
        out.writeBytes(payload);
        return out.toByteArray();
    }

    /** VP8X 확장 헤더: flags(1)+reserved(3)+width-1(3, LE)+height-1(3, LE). */
    private static byte[] vp8x(int width, int height) {
        int w = width - 1;
        int h = height - 1;
        return riff("VP8X", new byte[] {
                0, 0, 0, 0,
                (byte) w, (byte) (w >> 8), (byte) (w >> 16),
                (byte) h, (byte) (h >> 8), (byte) (h >> 16),
        });
    }

    /** 단순 손실(VP8): 프레임 태그(3)+시작 코드 0x9d012a(3)+폭(2, LE, 14비트)+높이(2, LE, 14비트). */
    private static byte[] vp8Lossy(int width, int height) {
        int w = width & 0x3FFF;
        int h = height & 0x3FFF;
        return riff("VP8 ", new byte[] {
                0, 0, 0,
                (byte) 0x9d, 0x01, 0x2a,
                (byte) w, (byte) (w >> 8),
                (byte) h, (byte) (h >> 8),
        });
    }

    /** 무손실(VP8L): 시그니처 0x2F + packed 32비트 LE(폭-1 14비트, 높이-1 14비트, alpha 1비트, version 3비트). */
    private static byte[] vp8Lossless(int width, int height) {
        long packed = (long) (width - 1) | ((long) (height - 1) << 14);
        return riff("VP8L", new byte[] {
                0x2F,
                (byte) packed, (byte) (packed >> 8), (byte) (packed >> 16), (byte) (packed >> 24),
        });
    }

    private static MockMultipartFile webpFile(byte[] bytes) {
        return new MockMultipartFile("file", "photo.webp", "image/webp", bytes);
    }

    @Test
    @DisplayName("VP8X(확장) 헤더의 정상 크기는 통과한다")
    void vp8x_withinLimit_succeeds() {
        String url = postImageService.store(webpFile(vp8x(100, 100)));

        assertThat(url).endsWith(".webp");
    }

    @Test
    @DisplayName("VP8X(확장) 헤더가 픽셀 수 상한을 넘으면 거절한다")
    void vp8x_exceedsPixelLimit_throws() {
        // 50,000,000픽셀 상한을 넘도록 10,000 x 10,000(1억 픽셀).
        assertThatThrownBy(() -> postImageService.store(webpFile(vp8x(10_000, 10_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메가픽셀");
    }

    @Test
    @DisplayName("VP8(단순 손실) 헤더의 정상 크기는 통과한다")
    void vp8Lossy_withinLimit_succeeds() {
        String url = postImageService.store(webpFile(vp8Lossy(100, 100)));

        assertThat(url).endsWith(".webp");
    }

    @Test
    @DisplayName("VP8(단순 손실) 헤더가 픽셀 수 상한을 넘으면 거절한다")
    void vp8Lossy_exceedsPixelLimit_throws() {
        assertThatThrownBy(() -> postImageService.store(webpFile(vp8Lossy(10_000, 10_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메가픽셀");
    }

    @Test
    @DisplayName("VP8L(무손실) 헤더의 정상 크기는 통과한다")
    void vp8Lossless_withinLimit_succeeds() {
        String url = postImageService.store(webpFile(vp8Lossless(100, 100)));

        assertThat(url).endsWith(".webp");
    }

    @Test
    @DisplayName("VP8L(무손실) 헤더가 픽셀 수 상한을 넘으면 거절한다")
    void vp8Lossless_exceedsPixelLimit_throws() {
        assertThatThrownBy(() -> postImageService.store(webpFile(vp8Lossless(10_000, 10_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("메가픽셀");
    }

    @Test
    @DisplayName("알 수 없는 WEBP 하위 형식은 크기를 확인할 수 없으므로 거절한다")
    void unknownSubFormat_isRejected() {
        MockMultipartFile file = webpFile(riff("ANIM", new byte[] { 1, 2, 3, 4 }));

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("크기 정보를 읽을 수 없습니다");
    }

    @Test
    @DisplayName("헤더가 잘려 있어 치수를 읽을 수 없으면 거절한다")
    void truncatedHeader_isRejected() {
        byte[] full = vp8x(100, 100);
        byte[] truncated = new byte[15]; // RIFF 헤더 + 청크 fourCC까지만, 치수 필드 전에 끊김
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        MockMultipartFile file = webpFile(truncated);

        assertThatThrownBy(() -> postImageService.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("크기 정보를 읽을 수 없습니다");
    }
}
