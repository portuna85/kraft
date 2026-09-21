package com.kraft.post.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 게시글 사진(Post.picture)을 로컬 디스크에 저장하고, 브라우저에서 접근 가능한 공개 URL을
 * 돌려준다. Post.picture는 이미지 바이너리가 아니라 "경로/URL"을 저장하는 필드라는 기존 설계
 * 의도를 그대로 구현한 것이다.
 * <p>
 * 저장 경로는 {@code /images/**}로 서빙되며({@link com.kraft.config.WebConfig}), 이 경로는
 * {@code SecurityConfig}에 이미 permitAll로 등록되어 있어 별도 보안 설정 변경이 필요 없다.
 */
@Service
public class PostImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /** 시그니처 판별에 필요한 앞부분 길이(WEBP의 "WEBP" 표시가 8~12바이트에 있다). */
    private static final int SIGNATURE_HEADER_LENGTH = 12;

    /**
     * WEBP 치수를 컨테이너 헤더에서 직접 읽는 데 필요한 길이(F04). VP8X 확장 헤더의 캔버스
     * 높이가 29바이트째에서 끝난다 — 가장 긴 경우를 기준으로 여유 있게 잡는다.
     */
    private static final int WEBP_DIMENSION_HEADER_LENGTH = 30;

    /** 압축을 풀었을 때의 픽셀 수 상한(약 50메가픽셀). 파일은 작지만 펼치면 거대한 이미지를 막는다. */
    private static final long MAX_PIXELS = 50_000_000L;

    /**
     * 아이폰 기본 촬영 포맷(HEIC/HEIF). 허용 목록에 넣지 않는 이유는 "받을 수 없어서"가 아니라
     * <b>받아봐야 대부분의 방문자에게 보이지 않기 때문</b>이다 — Chrome·Firefox·Edge는 HEIC를
     * 렌더링하지 못해 저장은 성공하고 화면에서만 깨진 이미지가 된다(Safari만 표시 가능).
     * 서버에서 JPG로 변환하려면 JDK에 없는 네이티브 디코더(libheif 등)가 필요해 범위를 넘는다.
     * <p>
     * 업로드 폼의 {@code accept}에 이미지 MIME 타입을 구체적으로 나열해두면(post-save.html,
     * post-update.html) iOS Safari가 대개 JPEG로 변환해 올려주므로 실제로 여기까지 오는 경우는
     * 많지 않지만, iOS 버전·"파일" 앱 경유 선택 등에서는 HEIC 그대로 도착한다. 그때 일반
     * "허용되지 않는 파일 형식입니다: heic" 메시지는 사용자가 할 수 있는 일을 알려주지 못하므로,
     * 아래 안내 문구로 따로 응답한다.
     */
    private static final Set<String> HEIF_EXTENSIONS = Set.of("heic", "heif");
    private static final String HEIF_MESSAGE =
            "아이폰 사진 형식(HEIC)은 일부 브라우저에서 표시되지 않아 업로드할 수 없습니다. "
                    + "아이폰 [설정] > [카메라] > [포맷]을 '높은 호환성'으로 바꾸면 JPG로 저장됩니다.";

    /** ISO base media file format 헤더에서 읽어야 할 길이와, HEIF 계열 브랜드 목록. */
    private static final int FTYP_HEADER_LENGTH = 12;
    private static final Set<String> HEIF_BRANDS =
            Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "hevm", "hevs", "mif1", "msf1");

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String store(MultipartFile file) {
        // 검증이 소문자로 정규화한 확장자를 그대로 파일명에 쓴다. 예전에는 검증만 소문자로 하고
        // 저장은 원본 확장자를 썼기 때문에, 휴대폰·카메라가 흔히 만드는 "IMG_0001.JPG"가
        // "uuid.JPG"로 저장됐다(대소문자를 구분하는 파일 시스템에서 문제가 될 수 있다).
        String extension = validate(file);
        String filename = UUID.randomUUID() + "." + extension;

        Path target = Path.of(uploadDir).resolve(filename);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 저장에 실패했습니다.", e);
        }

        return "/images/" + filename;
    }

    /** {@code store()}가 돌려주는 공개 URL의 접두어. */
    public static final String PUBLIC_PREFIX = "/images/";

    /**
     * 공개 URL에서 파일명만 뽑는다. {@code store()}가 만든 모양({@code /images/uuid.ext} —
     * 하위 경로 없는 파일명 하나)이 아니면 {@code null}을 돌려준다. url은 클라이언트가 보낸
     * 문자열이므로, 소유권 조회({@code PostImageRegistry})와 파일 삭제가 같은 판정을 쓰도록
     * 여기 한 곳에 모았다.
     */
    public static String fileNameOf(String url) {
        if (url == null || url.isBlank() || !url.startsWith(PUBLIC_PREFIX)) {
            return null;
        }
        String fileName = url.substring(PUBLIC_PREFIX.length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            return null;
        }
        return fileName;
    }

    /**
     * {@code store()}가 만든 공개 URL(예: {@code /images/uuid.png})을 근거로 실제 파일을
     * 지운다. url이 없거나 {@code /images/} 접두어가 아니면 조용히 무시한다(정리할 이미지가
     * 없는 정상 상태). url은 클라이언트가 요청 본문에 그대로 실어 보낸 문자열이므로, 정규화한
     * 경로가 업로드 디렉터리 밖을 가리키면(경로 조작 시도) 삭제하지 않고 조용히 무시한다 —
     * {@code store()}가 UUID로만 파일명을 만드는 것과 달리, 삭제는 클라이언트가 지정한 경로를
     * 다루므로 이 방어가 반드시 필요하다.
     */
    public void deleteIfExists(String url) {
        if (url == null || url.isBlank() || !url.startsWith("/images/")) {
            return;
        }

        Path base = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(url.substring("/images/".length())).normalize();
        if (!target.startsWith(base)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 삭제에 실패했습니다.", e);
        }
    }

    /**
     * 업로드 파일을 검증하고, 저장에 쓸 소문자 확장자를 돌려준다.
     */
    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }
        String extension = extensionOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        // 확장자만 보면 ".jpg"로 이름만 바뀐 HEIC 파일을 걸러내지 못한다(공유·복사 과정에서
        // 실제로 생긴다). 내용까지 확인해 같은 안내로 응답한다.
        if (HEIF_EXTENSIONS.contains(extension) || isHeif(file)) {
            throw new IllegalArgumentException(HEIF_MESSAGE);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + extension);
        }
        validateRealImage(file, extension);
        return extension;
    }

    /**
     * 확장자가 아니라 <b>내용</b>이 실제로 그 이미지 형식인지 확인한다.
     * <p>
     * 예전에는 일반 텍스트를 {@code not-an-image.png}로 올려도 그대로 저장됐다
     * (개선 보고서 F06). 두 단계로 막는다:
     * <ol>
     * <li>매직 바이트(파일 시그니처)가 확장자와 맞는지 — 이름만 바꾼 파일을 걸러낸다.</li>
     * <li>실제 크기를 읽어 픽셀 수 상한을 넘는지 — 파일은 작지만 압축을 풀면 거대한
     * 이미지(decompression bomb)를 걸러낸다. JPG/PNG/GIF는 {@link ImageIO}가, WEBP는
     * JDK 기본 ImageIO에 디코더가 없어 컨테이너 헤더를 직접 읽는
     * {@link #validateWebpPixelCount}가 담당한다(F04).</li>
     * </ol>
     */
    private void validateRealImage(MultipartFile file, String extension) {
        byte[] header = readHeader(file, SIGNATURE_HEADER_LENGTH);
        if (!matchesSignature(header, extension)) {
            throw new IllegalArgumentException(
                    "이미지 파일이 아니거나 확장자와 실제 형식이 다릅니다: " + extension);
        }
        validatePixelCount(file, extension);
    }

    private boolean matchesSignature(byte[] header, String extension) {
        return switch (extension) {
            case "png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38); // "GIF8"
            // RIFF....WEBP — 4~8바이트는 파일 크기라 건너뛴다.
            case "webp" -> startsWith(header, 0x52, 0x49, 0x46, 0x46)
                    && header.length >= 12
                    && "WEBP".equals(new String(header, 8, 4, StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    /**
     * 디코딩하지 않고 헤더에서 크기만 읽어 픽셀 수를 제한한다. 전체를 {@code ImageIO.read()}로
     * 펼치면 그 자체가 메모리를 먹으므로, 리더에게 폭·높이만 물어본다.
     * <p>
     * 원본 {@code InputStream}을 {@code ImageInputStream}과 별도로 try-with-resources에 넣는다
     * — {@code ImageIO.createImageInputStream()}이 돌려주는 래퍼(보통
     * {@code MemoryCacheImageInputStream})의 {@code close()}는 자기 내부 버퍼만 닫고 감싼
     * 원본 스트림은 닫지 않는다. 예전에는 원본 스트림을 변수 없이 바로 넘겨 그 스트림이 누수됐다
     * (개선 보고서 "이미지 입력 스트림 소유권").
     * <p>
     * WEBP는 표준 JDK ImageIO에 디코더가 없어({@code readers.hasNext()}가 false) 이 경로를
     * 타지 않는다 — {@link #validateWebpPixelCount}가 컨테이너 헤더를 직접 읽어 같은 상한을
     * 적용한다(F04). 예전에는 여기서 그냥 건너뛰어, WEBP는 시그니처만 맞으면 픽셀 수 제한
     * 없이 올라갔다.
     */
    private void validatePixelCount(MultipartFile file, String extension) {
        if ("webp".equals(extension)) {
            validateWebpPixelCount(file);
            return;
        }
        try (InputStream in = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(in)) {
            if (input == null) {
                return;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException(
                            "이미지 크기가 너무 큽니다. 가로×세로 " + MAX_PIXELS / 1_000_000 + "메가픽셀 이하만 올릴 수 있습니다.");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    /**
     * WEBP 컨테이너 헤더에서 캔버스 치수를 직접 읽어 픽셀 수 상한을 적용한다(F04). 세 가지
     * 하위 형식(단순 손실 VP8, 무손실 VP8L, 확장 VP8X)의 헤더 배치는 WebP 컨테이너
     * 명세(RIFF/WEBP 청크 구조)에 고정되어 있어 전체를 디코딩하지 않고도 폭·높이만 뽑을 수
     * 있다. 어떤 하위 형식인지도, 치수도 읽을 수 없으면(손상되었거나 알 수 없는 변형) 안전한
     * 쪽으로 거절한다 — 조용히 통과시키지 않는다.
     */
    private void validateWebpPixelCount(MultipartFile file) {
        byte[] h = readHeader(file, WEBP_DIMENSION_HEADER_LENGTH);
        long[] dimensions = webpDimensions(h);
        if (dimensions == null) {
            throw new IllegalArgumentException("WEBP 이미지의 크기 정보를 읽을 수 없습니다.");
        }
        long pixels = dimensions[0] * dimensions[1];
        if (pixels > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "이미지 크기가 너무 큽니다. 가로×세로 " + MAX_PIXELS / 1_000_000 + "메가픽셀 이하만 올릴 수 있습니다.");
        }
    }

    /**
     * @return {@code [width, height]} 또는 헤더가 너무 짧거나 알려진 하위 형식이 아니면 null.
     *         RIFF 헤더(0~11)는 이미 시그니처 검사가 확인했다고 가정하고 12바이트부터 읽는다.
     */
    private long[] webpDimensions(byte[] h) {
        if (h.length < 20) {
            return null;
        }
        String subFormat = new String(h, 12, 4, StandardCharsets.US_ASCII);
        return switch (subFormat) {
            case "VP8X" -> {
                if (h.length < 30) {
                    yield null;
                }
                // 24비트 리틀엔디언, "minus 1" 인코딩(실제 값은 이 필드 + 1).
                long width = (u(h[24]) | (u(h[25]) << 8) | (u(h[26]) << 16)) + 1;
                long height = (u(h[27]) | (u(h[28]) << 8) | (u(h[29]) << 16)) + 1;
                yield new long[] { width, height };
            }
            case "VP8 " -> {
                // 단순 손실 압축: 프레임 태그(3) + 시작 코드 0x9d012a(3) 다음에 14비트
                // 폭·높이가 각각 16비트 리틀엔디언에 담긴다(위 2비트는 스케일 값이라 버린다).
                if (h.length < 30) {
                    yield null;
                }
                long width = (u(h[26]) | (u(h[27]) << 8)) & 0x3FFF;
                long height = (u(h[28]) | (u(h[29]) << 8)) & 0x3FFF;
                yield new long[] { width, height };
            }
            case "VP8L" -> {
                // 무손실: 시그니처 바이트(0x2F) 다음 4바이트에 14비트 폭·높이(각 -1 인코딩)가
                // 리틀엔디언 32비트 값으로 packed 되어 있다.
                if (h.length < 25 || (h[20] & 0xFF) != 0x2F) {
                    yield null;
                }
                long packed = u(h[21]) | (u(h[22]) << 8) | (u(h[23]) << 16) | (u(h[24]) << 24);
                long width = (packed & 0x3FFF) + 1;
                long height = ((packed >> 14) & 0x3FFF) + 1;
                yield new long[] { width, height };
            }
            default -> null;
        };
    }

    private static long u(byte b) {
        return b & 0xFFL;
    }

    private byte[] readHeader(MultipartFile file, int length) {
        byte[] header = new byte[length];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(header, 0, length);
            return read < length ? java.util.Arrays.copyOf(header, read) : header;
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    private boolean startsWith(byte[] header, int... signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((header[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 파일 내용이 HEIF 계열인지 본다. HEIF/HEIC는 ISO base media file format이라
     * 4~8바이트가 {@code ftyp}, 이어지는 4바이트가 브랜드({@code heic}, {@code mif1} 등)다.
     * 헤더를 읽지 못하거나 12바이트보다 짧으면 판단하지 않고 {@code false}로 넘긴다 —
     * 확장자 검사가 그다음에 이어지므로 여기서 막지 않아도 안전하다.
     */
    private boolean isHeif(MultipartFile file) {
        byte[] header = new byte[FTYP_HEADER_LENGTH];
        try (InputStream in = file.getInputStream()) {
            if (in.readNBytes(header, 0, FTYP_HEADER_LENGTH) < FTYP_HEADER_LENGTH) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        String boxType = new String(header, 4, 4, StandardCharsets.US_ASCII);
        String brand = new String(header, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return "ftyp".equals(boxType) && HEIF_BRANDS.contains(brand);
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
