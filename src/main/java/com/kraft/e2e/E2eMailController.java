package com.kraft.e2e;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * E2E 테스트가 "방금 발송된 인증 메일"의 본문을 읽어 갈 수 있게 하는 전용 엔드포인트.
 * {@code @Profile("e2e")}가 붙어 있어 운영·로컬에서는 빈 자체가 만들어지지 않는다.
 * <p>
 * 이 엔드포인트가 있어야 이메일 인증 흐름 전체(가입 → 메일 링크 열기 → GUEST에서 USER로 승격
 * → 글쓰기 가능)를 실제 브라우저로 끝까지 검증할 수 있다.
 */
@RequiredArgsConstructor
@Profile("e2e")
@RestController
public class E2eMailController {

    private final RecordingEmailSender recordingEmailSender;

    @GetMapping("/e2e/mails/latest")
    public ResponseEntity<RecordingEmailSender.SentMail> latest(@RequestParam String to) {
        return recordingEmailSender.lastSentTo(to)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
