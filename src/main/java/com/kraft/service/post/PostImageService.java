package com.kraft.service.post;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String store(MultipartFile file) {
        validate(file);
        String extension = extensionOf(file.getOriginalFilename());
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

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }
        String extension = extensionOf(file.getOriginalFilename()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + extension);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
