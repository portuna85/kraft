package com.kraft.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * {@code spring.mail.*}(application.yml, 환경변수 기반)로 설정된 실제 SMTP 서버를 통해
 * 발송하는 유일한 {@link EmailSender} 구현체. 모든 프로파일(local·prod)이 이 빈을 쓴다 —
 * 로컬에서도 실제 메일함으로 인증 흐름을 끝까지 확인할 수 있게 하기 위해서다.
 */
@RequiredArgsConstructor
@Service
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
