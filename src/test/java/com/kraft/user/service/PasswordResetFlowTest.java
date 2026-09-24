package com.kraft.user.service;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.PasswordResetToken;
import com.kraft.user.domain.PasswordResetTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import com.kraft.user.mail.OutboxMailKind;
import com.kraft.user.mail.OutboxMailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비밀번호 재설정을 <b>진짜 트랜잭션과 DB 상태</b>로 검증한다. 단위 테스트는 "무엇을 호출했는가"만
 * 보므로, 이 기능에서 정작 중요한 두 가지를 놓친다: 비밀번호가 실제로 바뀌었는가, 그리고 만료
 * 토큰의 삭제가 뒤따르는 예외와 함께 롤백되지 않는가(F08과 같은 덫).
 */
@SpringBootTest
class PasswordResetFlowTest {

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private ExpiredTokenPurger expiredTokenPurger;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private OutboxMailRepository outboxMailRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        outboxMailRepository.deleteAll();

        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("reset-" + unique)
                .email("reset-" + unique + "@example.com")
                .password(passwordEncoder.encode("OldPass1!"))
                .role(Role.USER)
                .build());
    }

    private String saveToken(LocalDateTime expiresAt) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(expiresAt)
                .build());
        return token;
    }

    @Test
    @DisplayName("요청하면 재설정 토큰과 PASSWORD_RESET 메일이 함께 남는다")
    void request_storesTokenAndQueuesPasswordResetMail() {
        passwordResetService.request(user.getEmail());

        assertThat(tokenRepository.findAll()).hasSize(1);
        assertThat(outboxMailRepository.findAll())
                .singleElement()
                .satisfies(mail -> {
                    assertThat(mail.getKind()).isEqualTo(OutboxMailKind.PASSWORD_RESET);
                    // 대기열의 평문 토큰을 해시한 값이 저장된 해시와 같아야 메일의 링크가
                    // 실제로 동작한다(SEC-04로 저장 테이블에는 평문이 없다).
                    assertThat(EmailHasher.sha512Hex(mail.getToken()))
                            .isEqualTo(tokenRepository.findAll().get(0).getTokenHash());
                });
    }

    @Test
    @DisplayName("유효한 토큰으로 재설정하면 새 비밀번호로 바뀌고 토큰은 사라진다")
    void reset_changesStoredPasswordAndConsumesToken() {
        String token = saveToken(LocalDateTime.now().plusMinutes(10));

        passwordResetService.reset(token, "BrandNew1!");

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("BrandNew1!", reloaded.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("OldPass1!", reloaded.getPassword())).isFalse();
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(token))).isEmpty();
    }

    @Test
    @DisplayName("만료된 토큰으로 시도하면 예외가 나도 토큰은 DB에서 사라지고 비밀번호는 그대로다")
    void reset_withExpiredToken_purgesTokenWithoutChangingPassword() {
        String expired = saveToken(LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> passwordResetService.reset(expired, "BrandNew1!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료");

        // 같은 트랜잭션에서 지웠다면 이 예외와 함께 삭제도 롤백되어 토큰이 남아 있었을 것이다.
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(expired))).isEmpty();
        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPass1!", reloaded.getPassword())).isTrue();
    }

    @Test
    @DisplayName("다시 요청하면 앞서 보낸 링크는 무효가 된다 — 살아 있는 링크는 하나뿐이다")
    void request_invalidatesPreviouslyIssuedToken() {
        String first = saveToken(LocalDateTime.now().plusMinutes(10));

        passwordResetService.request(user.getEmail());

        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(first))).isEmpty();
        assertThat(tokenRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("아무도 누르지 않아 방치된 만료 링크는 정리 배치가 치운다")
    void purgeExpired_removesAbandonedResetTokens() {
        String expired = saveToken(LocalDateTime.now().minusHours(2));
        String valid = saveToken(LocalDateTime.now().plusMinutes(10));

        expiredTokenPurger.purgeExpired();

        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(expired))).isEmpty();
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(valid))).isPresent();
    }
}
