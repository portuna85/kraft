package com.kraft.shared.web;

import com.kraft.shared.exception.StorageException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@link ApiExceptionHandlerTest} 전용 더미 컨트롤러. 표준 MVC 예외(필수 파라미터·멀티파트
 * 파트 누락, 지원하지 않는 메서드·미디어 타입 등)만 일으킨다 — 실제 도메인 컨트롤러는 이 예외들을
 * 재현하기 번거롭기 때문이다.
 */
@RestController
public class ApiExceptionHandlerTestController {

    @GetMapping("/test/missing-param")
    public String missingParam(@RequestParam String required) {
        return required;
    }

    @PostMapping(value = "/test/missing-part", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public String missingPart(@RequestPart MultipartFile file) {
        return file.getOriginalFilename();
    }

    @GetMapping("/test/method-not-allowed")
    public String methodNotAllowed() {
        return "ok";
    }

    @PostMapping(value = "/test/media-type", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public String mediaType() {
        return "ok";
    }

    @GetMapping("/test/response-status")
    public String responseStatus() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "찾을 수 없습니다.");
    }

    @PostMapping("/test/illegal-argument")
    public String illegalArgument() {
        throw new IllegalArgumentException("잘못된 요청입니다.");
    }

    @PostMapping("/test/illegal-argument-non-korean")
    public String illegalArgumentNonKorean() {
        throw new IllegalArgumentException("Assertion failed");
    }

    @PostMapping("/test/storage-failure")
    public String storageFailure() {
        throw new StorageException("이미지 저장에 실패했습니다.", new java.io.IOException("disk full"));
    }

    @PostMapping("/test/access-denied")
    public String accessDenied() {
        throw new AccessDeniedException("작성자 본인 또는 관리자만 수정·삭제할 수 있습니다. id=123");
    }

    public record ValidatedBody(@NotBlank(message = "제목은 필수입니다.") String title,
                                 @NotBlank(message = "내용은 필수입니다.") String content) {
    }

    @PostMapping("/test/validation")
    public String validation(@Valid @RequestBody ValidatedBody body) {
        return "ok";
    }
}
