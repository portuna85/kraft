package com.kraft.user.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTest {

    private final JavaMailSender javaMailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(javaMailSender, "no-reply@kraft.io.kr", "login@example.com");

    @Test
    @DisplayName("받는 사람·제목·본문을 그대로 SMTP 발송기에 넘긴다")
    void send_passesThroughRecipientSubjectAndText() {
        sender.send("user@example.com", "[kraft] 제목", "본문입니다");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("[kraft] 제목");
        assertThat(message.getText()).isEqualTo("본문입니다");
    }

    @Test
    @DisplayName("From은 app.mail.from을 쓰고, 비어 있으면 SMTP 로그인 계정으로 물러난다 (BE-45)")
    void send_setsFromFromConfigOrSmtpUsername() {
        sender.send("user@example.com", "s", "t");
        new SmtpEmailSender(javaMailSender, " ", "login@example.com").send("user@example.com", "s", "t");
        new SmtpEmailSender(javaMailSender, "", "").send("user@example.com", "s", "t");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, org.mockito.Mockito.times(3)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(SimpleMailMessage::getFrom)
                .containsExactly("no-reply@kraft.io.kr", "login@example.com", null);
    }

    @Test
    @DisplayName("발송 실패는 삼키지 않고 그대로 던진다 — 아웃박스 워커가 재시도하려면 실패를 알아야 한다")
    void send_propagatesFailure() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.send("user@example.com", "s", "t"))
                .isInstanceOf(MailSendException.class);
    }
}
