package com.kraft.post.service;

import com.kraft.support.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

        String url = postImageService.store(file).url();

        assertThat(url).startsWith("/images/").endsWith(".png");
    }

    @Test
    @DisplayName("A-FE-09: store가 실제 픽셀 크기를 돌려준다(CLS 방지용 img width/height)")
    void store_returnsActualPixelDimensions() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", TestImages.pngBytes(64, 32));

        PostImageService.StoredImage stored = postImageService.store(file);

        assertThat(stored.width()).isEqualTo(64);
        assertThat(stored.height()).isEqualTo(32);
    }

    @Test
    @DisplayName("P2-3: store가 돌려주는 sizeBytes는 실제로 디스크에 저장된 파일 크기다(쿼터 기준)")
    void store_returnsStoredSizeUsedForQuota() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", TestImages.pngBytes(64, 32));

        PostImageService.StoredImage stored = postImageService.store(file);

        long onDisk = java.nio.file.Files.size(uploadDir.resolve(stored.url().substring("/images/".length())));
        assertThat(stored.sizeBytes()).isEqualTo(onDisk).isPositive();
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

        String url = postImageService.store(file).url();

        assertThat(url).endsWith(".jpg");
        assertThat(Files.exists(uploadDir.resolve(url.substring("/images/".length())))).isTrue();
    }

    @Test
    @DisplayName("A-SEC-10: 저장 파일에서 EXIF(GPS 등) 메타데이터가 사라진다")
    void store_stripsExifMetadataFromSavedFile() throws IOException {
        // ImageIO가 만든 순수 JPEG(EXIF 없음) 바로 뒤(SOI 다음)에 GPS 태그가 든 APP1을
        // 끼워 넣는다 — JPEG는 SOI 뒤 마커 순서를 엄격히 강제하지 않고, PostImageService의
        // 서명 검사도 앞 3바이트(FF D8 FF)만 본다.
        byte[] plain = TestImages.jpegFile("photo.jpg").getBytes();
        byte[] exifApp1 = exifApp1WithGpsPointer();
        byte[] withExif = new byte[2 + exifApp1.length + (plain.length - 2)];
        System.arraycopy(plain, 0, withExif, 0, 2);
        System.arraycopy(exifApp1, 0, withExif, 2, exifApp1.length);
        System.arraycopy(plain, 2, withExif, 2 + exifApp1.length, plain.length - 2);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", withExif);

        String url = postImageService.store(file).url();

        byte[] saved = readAll(uploadDir.resolve(url.substring("/images/".length())));
        assertThat(new String(saved, StandardCharsets.US_ASCII)).doesNotContain("Exif");
    }

    private static byte[] readAll(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** Orientation 없이 GPSInfoIFDPointer 태그 하나만 담은 최소 EXIF APP1. */
    private static byte[] exifApp1WithGpsPointer() {
        byte[] tiff = {
                'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00, // 헤더 + IFD0 오프셋(8)
                0x01, 0x00,                                   // 항목 1개
                0x25, (byte) 0x88, 0x04, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) 0xE7, 0x03, 0x00, 0x00, // GPSInfoIFDPointer
                0x00, 0x00, 0x00, 0x00,                       // 다음 IFD 없음
        };
        byte[] payload = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.US_ASCII), 0, payload, 0, 6);
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        int length = payload.length + 2;
        byte[] segment = new byte[4 + payload.length];
        segment[0] = (byte) 0xFF;
        segment[1] = (byte) 0xE1;
        segment[2] = (byte) (length >> 8);
        segment[3] = (byte) length;
        System.arraycopy(payload, 0, segment, 4, payload.length);
        return segment;
    }

    @Test
    @DisplayName("store: 업로드를 한 번만 읽고 열어 둔 입력 스트림이 없다 (BE-21)")
    void store_readsUploadOnceAndLeavesNoOpenStream() {
        CloseTrackingMultipartFile file =
                new CloseTrackingMultipartFile("file", "photo.png", "image/png", TestImages.pngBytes(1, 1));

        postImageService.store(file);

        assertThat(file.allStreamsClosed()).as("getInputStream()으로 연 스트림은 모두 닫혀야 한다").isTrue();
        assertThat(file.openedStreamCount()).as("검증·저장이 스트림을 다시 열지 않는다").isZero();
    }

    /** {@code getInputStream()}이 돌려준 스트림마다 {@code close()} 호출 여부를 기록한다. */
    private static class CloseTrackingMultipartFile extends MockMultipartFile {

        private final List<TrackingInputStream> opened = new ArrayList<>();

        CloseTrackingMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            super(name, originalFilename, contentType, content);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            TrackingInputStream stream = new TrackingInputStream(new ByteArrayInputStream(getBytes()));
            opened.add(stream);
            return stream;
        }

        boolean allStreamsClosed() {
            return opened.stream().allMatch(TrackingInputStream::isClosed);
        }

        int openedStreamCount() {
            return opened.size();
        }
    }

    private static class TrackingInputStream extends FilterInputStream {

        private boolean closed;

        TrackingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        boolean isClosed() {
            return closed;
        }
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
        // 예전에는 확장자만 봤기 때문에 이 파일이 그대로 저장됐다.
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
        String url = postImageService.store(file).url();
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
