package com.kraft.e2e;

import com.kraft.service.user.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * E2E 전용 {@link EmailSender}. 실제로 보내지 않고 마지막 메일들을 메모리에 담아둔다.
 * <p>
 * 가짜 발송기가 <b>있으면 좋은</b> 것이 아니라 <b>없으면 안 되는</b> 이유:
 * {@code EmailVerificationService.resend()}는 회원가입 경로와 달리 발송 실패를 흡수하지 않고
 * 그대로 전파한다. SMTP가 없는 환경에서는 인증메일 재발송 시나리오가 항상 500으로 끝나
 * 정상 흐름을 검증할 수 없다.
 * <p>
 * 담아둔 본문은 {@link E2eMailController}가 읽어준다. 그 덕분에 "가입 → 메일의 인증 링크를
 * 열어 승격 → 글쓰기 가능"이라는, 다른 방법으로는 자동화하기 어려운 흐름 전체를 E2E로 덮을 수 있다.
 * <p>
 * {@code @Primary}를 쓴 이유는 운영 코드({@link com.kraft.service.user.SmtpEmailSender})에
 * {@code @Profile("!e2e")} 같은 테스트용 표시를 남기지 않기 위해서다.
 */
@Slf4j
@Primary
@Profile("e2e")
@Component
public class RecordingEmailSender implements EmailSender {

    /** 메모리가 무한정 늘지 않도록 최근 것만 남긴다. */
    private static final int MAX_KEPT = 50;

    private final Deque<SentMail> sent = new ConcurrentLinkedDeque<>();

    public record SentMail(String to, String subject, String text) {
    }

    @Override
    public void send(String to, String subject, String text) {
        sent.addFirst(new SentMail(to, subject, text));
        while (sent.size() > MAX_KEPT) {
            sent.pollLast();
        }
        log.info("[E2E] 메일을 실제로 보내지 않고 기록했습니다. to={}, subject={}", to, subject);
    }

    /** 해당 주소로 간 가장 최근 메일. */
    public Optional<SentMail> lastSentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equals(to)).findFirst();
    }
}
