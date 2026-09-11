package com.kraft.service.user;

/**
 * 이메일 발송 추상화.
 *
 * @see SmtpEmailSender 모든 프로파일(local·docker·prod)이 쓰는 유일한 구현체 —
 * {@code JavaMailSender}로 실제 SMTP 발송
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}
