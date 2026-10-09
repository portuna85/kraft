package com.kraft.post.service;

import com.kraft.shared.exception.BusinessValidationException;
import com.kraft.shared.exception.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 게시글 사진을 검증해 로컬 디스크에 저장하고 공개 URL({@code /images/**},
 * {@link com.kraft.config.WebConfig})을 돌려준다.
 */
@Service
public class PostImageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    /** 시그니처 판별에 필요한 앞부분 길이(WEBP의 "WEBP" 표시가 8~12바이트에 있다). */
    private static final int SIGNATURE_HEADER_LENGTH = 12;

    /** 압축을 풀었을 때의 픽셀 수 상한(약 50메가픽셀). 파일은 작지만 펼치면 거대한 이미지를 막는다. */
    private static final long MAX_PIXELS = 50_000_000L;

    /**
     * 아이폰 HEIC/HEIF는 받지 않는다 — Safari 말고는 표시하지 못하고, 서버 변환에는 JDK에 없는 네이티브
     * 디코더가 필요하다. 사용자가 할 수 있는 일을 알려주는 전용 문구로 거절한다.
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

    /** 저장한 공개 URL과 픽셀 크기(상세 화면의 {@code <img width height>}로 CLS를 줄인다). */
    public record StoredImage(String url, int width, int height, long sizeBytes) {
    }

    public StoredImage store(MultipartFile file) {
        // 한 번만 읽어 검증·메타데이터 제거·저장이 같은 버퍼를 쓴다(5MB 검사 후).
        byte[] original = readUpload(file);
        // 파일명에는 소문자로 정규화한 확장자를 쓴다(".JPG" → ".jpg").
        ValidatedImage validated = validate(file, original);
        String filename = UUID.randomUUID() + "." + validated.extension();

        Path target = Path.of(uploadDir).resolve(filename);
        // GPS 좌표 등 위치·기기 정보가 담긴 메타데이터를 재인코딩 없이 제거한 뒤 저장한다.
        byte[] stripped = ImageMetadataStripper.strip(original, validated.extension());
        // 반쯤 쓰인 파일이 공개되지 않게 임시 파일에 쓴 뒤 원자적으로 옮긴다.
        Path temp = target.resolveSibling("." + filename + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.write(temp, stripped);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(temp);
            // 서버 디스크 문제라 500(StorageException).
            throw new StorageException("이미지 저장에 실패했습니다.", e);
        }

        return new StoredImage("/images/" + filename, validated.width(), validated.height(), stripped.length);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 임시 파일 정리는 최선 노력이다.
        }
    }

    private byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessValidationException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessValidationException("파일 크기는 5MB를 초과할 수 없습니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessValidationException("이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    /** {@code store()}가 돌려주는 공개 URL의 접두어. */
    public static final String PUBLIC_PREFIX = "/images/";

    /**
     * 공개 URL에서 파일명만 뽑는다. {@code /images/파일명} 모양이 아니면(하위 경로·{@code ..} 포함) null.
     * 클라이언트 입력이라 소유권 조회와 삭제가 이 판정을 함께 쓴다.
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
     * 공개 URL의 파일을 지운다. url이 없거나 {@code /images/}가 아니면 무시한다. 클라이언트가 보낸 경로라
     * 정규화 결과가 업로드 디렉터리 밖이면(경로 조작) 지우지 않는다.
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
            throw new StorageException("이미지 삭제에 실패했습니다.", e);
        }
    }

    /** 검증을 통과한 확장자와 실제 픽셀 크기. */
    private record ValidatedImage(String extension, int width, int height) {
    }

    /** 업로드 파일을 검증하고 소문자 확장자와 픽셀 크기를 돌려준다. */
    private ValidatedImage validate(MultipartFile file, byte[] data) {
        String extension = extensionOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        // ".jpg"로 이름만 바뀐 HEIC도 있어 내용까지 본다.
        if (HEIF_EXTENSIONS.contains(extension) || isHeif(data)) {
            throw new BusinessValidationException(HEIF_MESSAGE);
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessValidationException("허용되지 않는 파일 형식입니다: " + extension);
        }
        Dimensions dimensions = validateRealImage(data, extension);
        return new ValidatedImage(extension, dimensions.width(), dimensions.height());
    }

    /**
     * 내용이 실제로 그 형식인지 본다: 시그니처가 확장자와 맞는지, 픽셀 수가 상한 이하인지
     * (decompression bomb 방지).
     */
    private Dimensions validateRealImage(byte[] data, String extension) {
        byte[] header = java.util.Arrays.copyOf(data, Math.min(data.length, SIGNATURE_HEADER_LENGTH));
        if (!matchesSignature(header, extension)) {
            throw new BusinessValidationException(
                    "이미지 파일이 아니거나 확장자와 실제 형식이 다릅니다: " + extension);
        }
        return validatePixelCount(data, extension);
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

    /** 픽셀 크기. 0은 리더가 없어 읽지 못했다는 뜻이고, 화면은 width·height 속성을 생략한다. */
    private record Dimensions(int width, int height) {
        static final Dimensions UNKNOWN = new Dimensions(0, 0);
    }

    /** 디코딩하지 않고 리더에게 폭·높이만 물어 픽셀 수를 제한한다. WEBP는 JDK에 리더가 없어 따로 읽는다. */
    private Dimensions validatePixelCount(byte[] data, String extension) {
        if ("webp".equals(extension)) {
            return validateWebpPixelCount(data);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            if (input == null) {
                return Dimensions.UNKNOWN;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Dimensions.UNKNOWN;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (pixels > MAX_PIXELS) {
                    throw new BusinessValidationException(
                            "이미지 크기가 너무 큽니다. 가로×세로 " + MAX_PIXELS / 1_000_000 + "메가픽셀 이하만 올릴 수 있습니다.");
                }
                return new Dimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BusinessValidationException("이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    /** WEBP는 {@link WebpStructure}가 RIFF 구조를 끝까지 검사하고 캔버스 크기를 읽는다. */
    private Dimensions validateWebpPixelCount(byte[] data) {
        long[] dimensions = WebpStructure.inspect(data);
        long pixels = dimensions[0] * dimensions[1];
        if (pixels > MAX_PIXELS) {
            throw new BusinessValidationException(
                    "이미지 크기가 너무 큽니다. 가로×세로 " + MAX_PIXELS / 1_000_000 + "메가픽셀 이하만 올릴 수 있습니다.");
        }
        return new Dimensions((int) dimensions[0], (int) dimensions[1]);
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

    /** 4~8바이트가 {@code ftyp}이고 브랜드가 HEIF 계열인지. 12바이트 미만이면 false(뒤 검사가 막는다). */
    private boolean isHeif(byte[] data) {
        if (data.length < FTYP_HEADER_LENGTH) {
            return false;
        }
        byte[] header = data;

        String boxType = new String(header, 4, 4, StandardCharsets.US_ASCII);
        String brand = new String(header, 8, 4, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
        return "ftyp".equals(boxType) && HEIF_BRANDS.contains(brand);
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessValidationException("파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
