package com.kraft.user.service;

import com.kraft.post.domain.PostRepository;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B12: 문자 수(72자)와 실제 인코더가 다루는 바이트 수는 다르다 — DTO의
 * {@code @Size(max = 72)}는 문자 수만 보고, 실제로 저장에 쓰이는
 * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}(BCrypt)는 72
 * <b>바이트</b>를 기준으로 다룬다. 한글 72자는 UTF-8로 216바이트라 이 경계를 훨씬 넘는다.
 * <p>
 * 실제로 설치된 인코더로 확인한 결과: {@code PasswordLengthPolicyTest}의 기존 주석과 달리
 * "조용히 잘라 버리는" 것이 아니라 {@link IllegalArgumentException}("password cannot be
 * more than 72 bytes")을 <b>던진다</b>. 이 예외는 {@code ApiExceptionHandler}가 잡아
 * 400으로 바꾸지만, 메시지는 영문 원문 그대로 클라이언트에 노출된다 — DTO 검증은
 * "8자 이상 72자 이하"로 통과시킨 입력을 인코더가 거절하면서, 정작 사용자에게는
 * 그 이유를 한국어로 설명하지 않는 정책·메시지 불일치가 실제로 존재한다.
 * <p>
 * 이 테스트는 이 불일치를 고치지 않는다(정책 변경은 별도 판단 — 개선 보고서 B12). 실제
 * 동작을 그대로 기록으로 남기고, 이 경계를 넘지 않는 멀티바이트 입력(이모지 포함, 72바이트
 * 이내)은 가입→변경→재설정 전 과정에서 일관되게 동작하는지도 함께 확인한다.
 */
@SpringBootTest
class PasswordMultibyteBoundaryTest {

    /** DTO의 @Size(max=72)는 통과하지만(72자) UTF-8로는 216바이트라 인코더의 72바이트 경계를 넘는다. */
    private static final String KOREAN_72 = "가".repeat(72);

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailTokenRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void setUp() {
        emailTokenRepository.deleteAll();
        resetTokenRepository.deleteAll();
        outboxMailRepository.deleteAll();
        postRepository.deleteAll();
        userRepository.deleteAll();
        email = "multibyte-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    @Test
    @DisplayName("B12: DTO 검증을 통과하는 72자 한글 비밀번호(216바이트)는 실제 가입에서 인코더가 거절한다 — 조용한 절단이 아니다")
    void koreanPassword72Chars_exceedsEncoderByteLimit_signUpRejectsLoudly() {
        assertThat(KOREAN_72.getBytes(StandardCharsets.UTF_8).length)
                .as("한글 72자는 72바이트를 훨씬 넘는다 — 이 테스트가 실제로 경계를 넘는 입력을 쓰는지 확인")
                .isGreaterThan(72);

        // DTO의 @Size(max=72)는 문자 수만 보므로 이 입력을 통과시키지만, 실제 저장 시점의
        // 인코더는 바이트 수 기준이라 여기서 예외로 거절한다 — signUp 자체가 실패한다.
        assertThatThrownBy(() -> userService.signUp("multibyte-tester", email, KOREAN_72))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password cannot be more than 72 bytes");

        assertThat(userRepository.findByEmailHash(com.kraft.user.domain.EmailHasher.sha512Hex(email))).isEmpty();
    }

    @Test
    @DisplayName("B12: 72바이트 경계 안쪽의 멀티바이트(이모지 포함) 비밀번호는 가입·변경·재설정 전 과정이 일관된다")
    void multibytePasswordWithinByteLimit_worksAcrossSignupAndChange() {
        String initial = "이모지🙂" + "a".repeat(59); // 72바이트 경계 안쪽
        assertThat(initial.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);

        userService.signUp("multibyte-tester2", email, initial);
        var user = userRepository.findByEmailHash(com.kraft.user.domain.EmailHasher.sha512Hex(email)).orElseThrow();
        userService.promoteToUser(user.getId());

        assertThat(passwordEncoder.matches(initial, user.getPassword())).isTrue();

        String changedTo = "한글비번" + "b".repeat(50);
        assertThat(changedTo.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(72);
        userService.changePassword(email, initial, changedTo);

        var afterChange = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(changedTo, afterChange.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(initial, afterChange.getPassword())).isFalse();
    }
}
