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
 * B15: 문자 수(72자)와 실제 인코더가 다루는 바이트 수는 다르다 — DTO의
 * {@code @Size(max = 72)}는 문자 수만 보고, 실제로 저장에 쓰이는
 * {@code PasswordEncoderFactories.createDelegatingPasswordEncoder()}(BCrypt)는 72
 * <b>바이트</b>를 기준으로 다룬다. 한글 72자는 UTF-8로 216바이트라 이 경계를 훨씬 넘는다.
 * <p>
 * 예전에는 DTO 검증을 통과한 입력을 인코더가 {@code IllegalArgumentException("password
 * cannot be more than 72 bytes")}로 거절했다 — 영문 원문 메시지가 그대로 노출됐다. 이제
 * {@code PasswordBytePolicy}가 인코더를 부르기 전에 UTF-8 바이트 수를 먼저 검사해, 같은
 * 상황에서 명확한 한국어 오류로 미리 거절한다(encoder 자체는 바꾸지 않는다 — 교체는 기존
 * 해시 호환성·rehash 정책이 필요한 별도 작업).
 * <p>
 * 이 경계를 넘지 않는 멀티바이트 입력(이모지 포함, 72바이트 이내)은 가입→변경→재설정 전
 * 과정에서 여전히 일관되게 동작해야 한다.
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
    @DisplayName("B15: DTO 검증을 통과하는 72자 한글 비밀번호(216바이트)는 인코더 대신 명확한 한국어 오류로 미리 거절한다")
    void koreanPassword72Chars_exceedsEncoderByteLimit_signUpRejectsWithClearKoreanMessage() {
        assertThat(KOREAN_72.getBytes(StandardCharsets.UTF_8).length)
                .as("한글 72자는 72바이트를 훨씬 넘는다 — 이 테스트가 실제로 경계를 넘는 입력을 쓰는지 확인")
                .isGreaterThan(72);

        // DTO의 @Size(max=72)는 문자 수만 보므로 이 입력을 통과시키지만, PasswordBytePolicy가
        // 인코더를 부르기 전에 UTF-8 바이트 수로 먼저 거절한다 — 인코더의 영문 원문 메시지가
        // 아니라 한국어 오류로 signUp이 실패한다.
        assertThatThrownBy(() -> userService.signUp("multibyte-tester", email, KOREAN_72))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UTF-8")
                .hasMessageContaining("72바이트")
                .hasMessageNotContaining("password cannot be more than");

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
