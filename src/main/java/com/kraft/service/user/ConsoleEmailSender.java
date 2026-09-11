package com.kraft.service.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 로컬 개발 프로파일(H2) 전용 {@link EmailSender}. 실제 SMTP로 발송하지 않고, 인증 링크를
 * 포함한 이메일 내용을 애플리케이션 로그에 그대로 남긴다. SMTP 자격 증명 없이도 이메일 인증
 * 플로우 전체(토큰 발급 → 링크 확인 → 인증 완료)를 로컬에서 끝까지 검증할 수 있게 해준다.
 * <p>
 * docker 프로파일은 2026-09-11부터 실제 SMTP로 보내는 {@link SmtpEmailSender}를 쓴다
 * (docker-compose로 띄운 실제 MariaDB로 검증하는 것과 같은 이유로, 메일도 실제 수신함으로
 * 검증할 수 있게 하기 위해서다).
 */
@Slf4j
@Service
@Profile("local")
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("""
                ===== [개발용] 이메일 발송 시뮬레이션 (실제 SMTP 미사용) =====
                받는 사람: {}
                제목: {}
                내용:
                {}
                ============================================================""",
                to, subject, text);
    }
}
