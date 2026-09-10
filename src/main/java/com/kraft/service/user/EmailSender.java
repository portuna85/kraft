package com.kraft.service.user;

/**
 * 이메일 발송 추상화. 프로파일에 따라 구현체가 바뀐다.
 *
 * @see ConsoleEmailSender 로컬 개발용 — 실제 발송 없이 콘솔 로그로 시뮬레이션
 * @see SmtpEmailSender 운영용 — {@code JavaMailSender}로 실제 SMTP 발송
 */
public interface EmailSender {

    void send(String to, String subject, String text);
}
