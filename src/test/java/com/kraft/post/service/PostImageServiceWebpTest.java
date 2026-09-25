package com.kraft.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PostImageService}의 WEBP 검증. JDK ImageIO에는 WEBP 디코더가 없어 컨테이너 구조를
 * 직접 검사하는 경로({@link WebpStructure})를 탄다.
 * <p>
 * 허용 쪽은 <b>실제 인코더가 만든 파일</b>로 확인한다. {@code src/test/resources/images/webp/}의
 * 파일은 Chromium canvas({@code toDataURL('image/webp')})가 인코딩한 손실·무손실·알파 이미지다.
 * 여기서 이미지 청크만 떼어 단순 형식으로 다시 감싼 파일과, 같은 VP8 프레임 2개로 만든
 * 애니메이션도 있다. 모두 Chromium {@code createImageBitmap}으로 64×48 디코딩을 확인한 뒤
 * 넣었다(평가 보고서 2026-09-25 F06).
 * <p>
 * 거절 쪽은 명세의 바이트 배치를 손으로 만든다. 예전 검사는 앞 30바이트의 치수만 읽어, 이미지
 * 데이터 없는 헤더만의 파일을 받아들였다.
 */
class PostImageServiceWebpTest {

    @TempDir
    Path uploadDir;

    private PostImageService postImageService;

    @BeforeEach
    void setUp() {
        postImageService = new PostImageService();
        ReflectionTestUtils.setField(postImageService, "uploadDir", uploadDir.toString());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "lossy", "lossless", "alpha", "simple-lossy", "simple-lossless", "animated" })
    @DisplayName("실제 인코더가 만든 WEBP(손실·무손실·알파·단순 형식·애니메이션)는 저장된다")
    void realWebp_isAccepted(String name) {
        String url = postImageService.store(webpFile(fixture(name)));

        assertThat(url).endsWith(".webp");
        assertThat(uploadDir.resolve(PostImageService.fileNameOf(url))).exists();
    }

    @Test
    @DisplayName("F06: 이미지 데이터 없이 30바이트 VP8X 헤더만 있는 파일은 거절한다")
    void headerOnlyVp8x_isRejected() {
        byte[] headerOnly = riff(chunk("VP8X", vp8xPayload(0, 100, 100)));

        assertRejected(headerOnly, WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: 파일 끝이 잘리면(RIFF 크기와 실제 길이 불일치) 거절한다")
    void truncatedFile_isRejected() {
        byte[] real = fixture("lossy");

        assertRejected(Arrays.copyOf(real, real.length - 100), WebpStructure.INVALID_MESSAGE);
        assertRejected(Arrays.copyOf(real, 15), WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: RIFF 크기 필드가 실제보다 크거나, 뒤에 다른 데이터가 붙으면 거절한다")
    void riffSizeMismatch_isRejected() {
        byte[] real = fixture("simple-lossy");
        byte[] trailing = Arrays.copyOf(real, real.length + 4);
        byte[] oversizedField = real.clone();
        writeLe32(oversizedField, 4, real.length);

        assertRejected(trailing, WebpStructure.INVALID_MESSAGE);
        assertRejected(oversizedField, WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: VP8 청크의 첫 파티션이 청크 안에 없으면(헤더만 남은 프레임) 거절한다")
    void vp8WithoutPartitionData_isRejected() {
        assertRejected(riff(chunk("VP8 ", vp8Payload(100, 100, 500, 0))), WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: VP8 시작 코드가 틀리거나 크기가 0이면 거절한다")
    void vp8BadStartCodeOrZeroSize_isRejected() {
        byte[] badStart = vp8Payload(100, 100, 4, 4);
        badStart[3] = 0;

        assertRejected(riff(chunk("VP8 ", badStart)), WebpStructure.INVALID_MESSAGE);
        assertRejected(riff(chunk("VP8 ", vp8Payload(0, 100, 4, 4))), WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: VP8L 헤더 5바이트뿐이고 비트스트림이 없으면 거절한다")
    void vp8lHeaderOnly_isRejected() {
        assertRejected(riff(chunk("VP8L", vp8lPayload(100, 100, 0))), WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: VP8X 캔버스 크기가 실제 이미지 크기와 다르면 거절한다")
    void vp8xCanvasMismatch_isRejected() {
        byte[] file = riff(chunk("VP8X", vp8xPayload(0, 200, 200)), chunk("VP8L", vp8lPayload(100, 100, 16)));

        assertRejected(file, WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("F06: 애니메이션 플래그가 있는데 프레임(ANMF)이 없으면 거절한다")
    void animatedWithoutFrames_isRejected() {
        byte[] file = riff(chunk("VP8X", vp8xPayload(0x02, 100, 100)), chunk("ANIM", new byte[6]));

        assertRejected(file, WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("단순 형식의 첫 청크가 이미지가 아니면 거절한다")
    void unknownFirstChunk_isRejected() {
        assertRejected(riff(chunk("ANIM", new byte[] { 1, 2, 3, 4 })), WebpStructure.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("구조가 온전해도 픽셀 수 상한을 넘으면 거절한다(VP8·VP8L·VP8X)")
    void exceedsPixelLimit_isRejected() {
        assertRejected(riff(chunk("VP8 ", vp8Payload(10_000, 10_000, 4, 4))), "메가픽셀");
        assertRejected(riff(chunk("VP8L", vp8lPayload(10_000, 10_000, 16))), "메가픽셀");
        assertRejected(riff(chunk("VP8X", vp8xPayload(0, 10_000, 10_000)),
                chunk("VP8L", vp8lPayload(10_000, 10_000, 16))), "메가픽셀");
    }

    @Test
    @DisplayName("구조를 만족하는 합성 파일은 상한 이내면 통과한다(검사가 지나치게 엄격하지 않다)")
    void structurallyValidSynthetic_isAccepted() {
        String url = postImageService.store(webpFile(riff(chunk("VP8L", vp8lPayload(100, 100, 16)))));

        assertThat(url).endsWith(".webp");
    }

    private void assertRejected(byte[] bytes, String message) {
        assertThatThrownBy(() -> postImageService.store(webpFile(bytes)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
        // 거절된 업로드는 디스크에 아무것도 남기지 않는다(검증이 저장보다 먼저다).
        try (var files = Files.list(uploadDir)) {
            assertThat(files).isEmpty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ── 바이트 조립 ─────────────────────────────────────────────────────────────

    private static byte[] fixture(String name) {
        try (InputStream in = PostImageServiceWebpTest.class.getResourceAsStream("/images/webp/" + name + ".webp")) {
            assertThat(in).as("fixture " + name).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MockMultipartFile webpFile(byte[] bytes) {
        return new MockMultipartFile("file", "photo.webp", "image/webp", bytes);
    }

    private static byte[] riff(byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        for (byte[] c : chunks) {
            body.writeBytes(c);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(body.size()));
        out.writeBytes(body.toByteArray());
        return out.toByteArray();
    }

    private static byte[] chunk(String fourCc, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourCc.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(payload.length));
        out.writeBytes(payload);
        if (payload.length % 2 == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    /** VP8X: flags(1) + reserved(3) + 캔버스 폭-1(3) + 높이-1(3). */
    private static byte[] vp8xPayload(int flags, int width, int height) {
        return new byte[] {
                (byte) flags, 0, 0, 0,
                (byte) (width - 1), (byte) ((width - 1) >> 8), (byte) ((width - 1) >> 16),
                (byte) (height - 1), (byte) ((height - 1) >> 8), (byte) ((height - 1) >> 16),
        };
    }

    /**
     * VP8 키 프레임: 프레임 태그(3, 첫 파티션 크기 19비트를 5비트 위치에) + 시작 코드 + 폭·높이
     * + 실제로 붙이는 데이터(dataBytes).
     */
    private static byte[] vp8Payload(int width, int height, int firstPartitionSize, int dataBytes) {
        int tag = firstPartitionSize << 5 | 0x10; // 키 프레임(bit0=0), show_frame=1
        byte[] payload = new byte[10 + dataBytes];
        payload[0] = (byte) tag;
        payload[1] = (byte) (tag >> 8);
        payload[2] = (byte) (tag >> 16);
        payload[3] = (byte) 0x9D;
        payload[4] = 0x01;
        payload[5] = 0x2A;
        payload[6] = (byte) width;
        payload[7] = (byte) ((width >> 8) & 0x3F);
        payload[8] = (byte) height;
        payload[9] = (byte) ((height >> 8) & 0x3F);
        return payload;
    }

    /** VP8L: 시그니처 0x2F + packed(폭-1, 높이-1, alpha 0, version 0) + 비트스트림(dataBytes). */
    private static byte[] vp8lPayload(int width, int height, int dataBytes) {
        long packed = (long) (width - 1) | ((long) (height - 1) << 14);
        byte[] payload = new byte[5 + dataBytes];
        payload[0] = 0x2F;
        payload[1] = (byte) packed;
        payload[2] = (byte) (packed >> 8);
        payload[3] = (byte) (packed >> 16);
        payload[4] = (byte) (packed >> 24);
        return payload;
    }

    private static byte[] le32(int v) {
        return new byte[] { (byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24) };
    }

    private static void writeLe32(byte[] target, int offset, int v) {
        System.arraycopy(le32(v), 0, target, offset, 4);
    }
}
