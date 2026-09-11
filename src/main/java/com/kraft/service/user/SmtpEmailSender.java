package com.kraft.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 운영·docker 프로파일용 {@link EmailSender}. {@code spring.mail.*}(환경변수 기반)로 설정된
 * 실제 SMTP 서버를 통해 발송한다. {@code JavaMailSender} 빈은 {@code spring.mail.host}가
 * 설정된 경우에만 Spring Boot가 자동 구성하므로(local 프로파일에는 없음), 이 빈은 prod·docker
 * 프로파일에서만 정상적으로 주입된다.
 */
@RequiredArgsConstructor
@Service
@Profile({"prod", "docker"})
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        javaMailSender.send(message);
    }
}
