package com.kraft.user.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * {@code spring.mail.*}(application.yml, 환경변수 기반)로 설정된 실제 SMTP 서버를 통해
 * 발송하는 유일한 {@link EmailSender} 구현체. 모든 프로파일(local·prod)이 이 빈을 쓴다 —
 * 로컬에서도 실제 메일함으로 인증 흐름을 끝까지 확인할 수 있게 하기 위해서다.
 */
@Service
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;
    /** 비어 있으면 From을 지정하지 않는다(서버 기본값에 맡긴다). */
    private final String from;

    /**
     * From 주소는 {@code app.mail.from}, 없으면 SMTP 로그인 계정({@code spring.mail.username})을 쓴다.
     * 지정하지 않으면 SMTP 서버가 임의의 기본 발신자를 붙여 SPF/DMARC 정렬에 불리하다.
     */
    public SmtpEmailSender(JavaMailSender javaMailSender,
                           @Value("${app.mail.from:}") String configuredFrom,
                           @Value("${spring.mail.username:}") String smtpUsername) {
        this.javaMailSender = javaMailSender;
        this.from = !configuredFrom.isBlank() ? configuredFrom.trim() : smtpUsername.trim();
    }

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isEmpty()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender.send(message);
    }
}
