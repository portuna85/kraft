// src/main/java/com/example/board/file/AttachmentService.java
package com.example.board.file;

import com.example.board.member.Member;
import com.example.board.member.Role;
import com.example.board.post.Post;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Set<String> IMAGE_EXT = Set.of("jpg","jpeg","png","gif","webp");
    private static final Set<String> IMAGE_CT  = Set.of("image/jpeg","image/png","image/gif","image/webp");

    private final AttachmentRepository attachmentRepository;

    /** 상대경로면 프로젝트 루트 기준으로 생성됩니다. (기본: uploads) */
    private final String uploadDir;
    /** 업로드 허용 최대 MB (기본: 10MB) */
    private final long maxSizeMb;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.upload.max-size-mb:10}") long maxSizeMb
    ) {
        this.attachmentRepository = attachmentRepository;
        this.uploadDir = uploadDir;
        this.maxSizeMb = maxSizeMb;
    }

    /** 파일 저장 + 썸네일(webp) 생성; 이미지 매직바이트/확장자/콘텐츠타입 모두 검사 */
    public Attachment store(MultipartFile file, Post post) {
        if (file == null || file.isEmpty()) return null;

        // 1) 용량 제한
        long maxBytes = maxSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "파일이 너무 큽니다.");
        }

        // 2) 확장자/Content-Type 1차 필터
        String ext = getExt(file.getOriginalFilename()).toLowerCase();
        boolean extOk = IMAGE_EXT.contains(ext);
        boolean ctOk  = file.getContentType() != null && IMAGE_CT.contains(file.getContentType());
        if (!(extOk && ctOk)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "이미지 파일만 업로드할 수 있습니다.(jpg/jpeg/png/gif/webp)");
        }

        // 3) 매직 바이트(헤더) 엄격 검사
        try (InputStream is = file.getInputStream()) {
            byte[] head = is.readNBytes(16);
            if (!FileMagic.isSupportedImage(head)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일을 읽을 수 없습니다.", e);
        }

        // 4) 저장 경로 준비
        Path base = resolveBase(uploadDir);
        try { Files.createDirectories(base); }
        catch (IOException e) { throw new RuntimeException("업로드 디렉터리 생성 실패: " + base, e); }

        // 5) 저장 파일명 생성 (원본명은 DB에만 기록)
        String stored = UUID.randomUUID().toString() + (ext.isEmpty() ? "" : "." + ext);
        Path dest = base.resolve(stored);

        // 6) 실제 저장
        try {
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            throw new RuntimeException("파일 저장에 실패했습니다.", e);
        }

        // 7) 썸네일 생성(webp, 300x300)
        String thumbUrl = null;
        if (file.getContentType() != null && file.getContentType().startsWith("image")) {
            String thumb = "thumb_" + stored + ".webp";
            Path thumbPath = base.resolve(thumb);
            try {
                Thumbnails.of(dest.toFile())
                        .size(300, 300)
                        .outputFormat("webp")
                        .toFile(thumbPath.toFile());
                thumbUrl = "/uploads/" + thumb;
            } catch (IOException e) {
                // 썸네일 실패는 원본 보존 후 안내
                throw new RuntimeException("썸네일 생성에 실패했습니다.", e);
            }
        }

        // 8) URL 구성 후 엔티티 생성 (DB 저장은 호출부 정책에 맞게)
        String url = "/uploads/" + stored;
        return new Attachment(
                post,
                file.getOriginalFilename(),
                stored,
                url,
                thumbUrl,
                file.getContentType(),
                file.getSize()
        );
    }

    /** 첨부 삭제(권한: 작성자 or ADMIN) + 물리 파일 삭제 */
    public void delete(Long attachmentId, Member requester) {
        var att = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."));

        var post = att.getPost();
        boolean isOwner = post.getAuthor().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == Role.ADMIN;

        if (!(isOwner || isAdmin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "작성자만 첨부파일을 삭제할 수 있습니다.");
        }

        Path base = resolveBase(uploadDir);
        try {
            if (att.getUrl() != null) {
                Files.deleteIfExists(base.resolve(att.getUrl().replace("/uploads/", "")));
            }
            if (att.getThumbnailUrl() != null) {
                Files.deleteIfExists(base.resolve(att.getThumbnailUrl().replace("/uploads/", "")));
            }
        } catch (IOException ignore) {
            // 물리 파일이 없어도 DB 삭제는 진행
        }

        attachmentRepository.delete(att);
    }

    private Path resolveBase(String dir) {
        Path p = Path.of(dir);
        if (p.isAbsolute()) return p;
        return Path.of(System.getProperty("user.dir")).resolve(dir);
    }

    private String getExt(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : "";
    }
}
