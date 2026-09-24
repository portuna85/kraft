package com.kraft.user.service;

import com.kraft.user.domain.EmailHasher;
import com.kraft.user.domain.EmailVerificationToken;
import com.kraft.user.domain.EmailVerificationTokenRepository;
import com.kraft.user.domain.Role;
import com.kraft.user.domain.User;
import com.kraft.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 만료된 인증 토큰이 <b>실제로 DB에서 사라지는지</b> 진짜 트랜잭션으로 검증한다
 * (개선 보고서 F08).
 * <p>
 * 예전에는 {@code verify()}가 만료 토큰을 지운 직후 예외를 던졌고, 쓰기 트랜잭션에서 런타임
 * 예외가 나가면 Spring 기본 롤백 규칙에 따라 그 삭제까지 되돌아갔다. 그래서 "만료 토큰은
 * 지운다"는 정책이 한 번도 지켜지지 않았다. 기존 단위 테스트는 {@code delete()} <b>호출 여부</b>만
 * 확인했기 때문에 이것을 놓쳤다 — 그래서 여기서는 호출이 아니라 트랜잭션이 끝난 뒤의
 * DB 상태를 본다.
 */
@SpringBootTest
class ExpiredTokenPurgeTest {

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private ExpiredTokenPurger expiredTokenPurger;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    /**
     * 회원 테이블을 통째로 비우지 않는다. 다른 테스트 클래스가 남긴 게시글이 회원을 참조하고
     * 있어 FK에 걸리기 때문이다. 이 테스트만의 회원을 매번 새로 만들어 서로 간섭하지 않게 한다.
     */
    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        user = userRepository.save(User.builder()
                .name("purge-" + unique)
                .email("purge-" + unique + "@example.com")
                .password("encoded")
                .role(Role.GUEST)
                .build());
    }

    @Test
    @DisplayName("F08: 만료된 링크로 인증을 시도하면 예외가 나도 토큰은 DB에서 사라진다")
    void verify_withExpiredToken_actuallyRemovesItFromDatabase() {
        String token = saveToken(LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> emailVerificationService.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("만료되었습니다");

        // 예전에는 예외가 트랜잭션을 롤백시켜 이 토큰이 그대로 남아 있었다.
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(token))).isEmpty();
        // 만료 검사 자체는 계속 동작하므로 권한이 올라가지도 않는다.
        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.GUEST);
    }

    @Test
    @DisplayName("유효한 토큰으로 인증하면 승격되고 토큰은 일회용으로 사라진다")
    void verify_withValidToken_promotesUserAndConsumesToken() {
        String token = saveToken(LocalDateTime.now().plusHours(1));

        emailVerificationService.verify(token);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(token))).isEmpty();
    }

    @Test
    @DisplayName("F08: 아무도 누르지 않아 방치된 만료 토큰은 정리 배치가 치운다")
    void purgeExpired_removesAbandonedExpiredTokens() {
        String expired = saveToken(LocalDateTime.now().minusDays(2));
        String valid = saveToken(LocalDateTime.now().plusHours(1));

        expiredTokenPurger.purgeExpired();

        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(expired))).isEmpty();
        assertThat(tokenRepository.findByTokenHash(EmailHasher.sha512Hex(valid))).isPresent();
    }

    private String saveToken(LocalDateTime expiresAt) {
        String token = UUID.randomUUID().toString();
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash(EmailHasher.sha512Hex(token))
                .user(user)
                .expiresAt(expiresAt)
                .build());
        return token;
    }
}
