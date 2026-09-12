package com.kraft.service.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return extension;
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
