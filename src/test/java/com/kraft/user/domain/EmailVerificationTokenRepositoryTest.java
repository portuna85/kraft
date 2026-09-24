package com.kraft.user.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EmailVerificationTokenRepository} 통합 테스트. 감사 필드가 없는 엔티티라
 * {@code JpaConfig}(EnableJpaAuditing) import는 불필요하다({@code CommentRepositoryTest}와 달리).
 * {@code User}를 저장하므로 {@link EmailAttributeConverter}는 import해야 한다.
 */
@DataJpaTest
@Import(EmailAttributeConverter.class)
class EmailVerificationTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
                User.builder().name("tester").email("tester@example.com").password("pw").role(Role.GUEST).build());
    }

    @Test
    @DisplayName("findByTokenHash: 존재하는 토큰이면 회원 정보와 함께 조회된다")
    void findByTokenHash_whenTokenExists_returnsTokenWithUser() {
        EmailVerificationToken saved = tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash("token-abc")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());
        em.flush();
        em.clear();

        Optional<EmailVerificationToken> found = tokenRepository.findByTokenHash("token-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getEmail()).isEqualTo("tester@example.com");
    }

    @Test
    @DisplayName("findByTokenHash: 존재하지 않는 토큰이면 빈 Optional을 반환한다")
    void findByTokenHash_whenTokenDoesNotExist_returnsEmptyOptional() {
        Optional<EmailVerificationToken> found = tokenRepository.findByTokenHash("no-such-token");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("deleteByUserId: 이 회원의 토큰만 지우고 다른 회원 것은 남긴다")
    void deleteByUserId_deletesOnlyTokensOfGivenUser() {
        User other = userRepository.save(
                User.builder().name("other").email("other@example.com").password("pw").role(Role.GUEST).build());
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash("mine").user(user).expiresAt(LocalDateTime.now().plusHours(1)).build());
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash("others").user(other).expiresAt(LocalDateTime.now().plusHours(1)).build());
        em.flush();

        tokenRepository.deleteByUserId(user.getId());

        assertThat(tokenRepository.findByTokenHash("mine")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("others")).isPresent();
    }

    @Test
    @DisplayName("deleteByExpiresAtBefore: 만료된 토큰만 지우고 지운 개수를 돌려준다")
    void deleteByExpiresAtBefore_deletesOnlyExpiredTokensAndReturnsCount() {
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash("expired").user(user).expiresAt(LocalDateTime.now().minusMinutes(1)).build());
        tokenRepository.save(EmailVerificationToken.builder()
                .tokenHash("valid").user(user).expiresAt(LocalDateTime.now().plusHours(1)).build());
        em.flush();

        int deleted = tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("valid")).isPresent();
    }
}
