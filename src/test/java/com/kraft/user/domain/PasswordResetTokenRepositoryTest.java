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
 * {@link PasswordResetTokenRepository} 통합 테스트. {@code deleteByUserId}·
 * {@code deleteByExpiresAtBefore}를 파생 삭제에서 벌크 JPQL DELETE로 바꿨으므로
 * (개선 보고서 "파생 delete 메서드의 엔티티별 삭제") 그 동작을 직접 검증한다.
 */
@DataJpaTest
@Import(EmailAttributeConverter.class)
class PasswordResetTokenRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(
                User.builder().name("tester").email("tester@example.com").password("pw").role(Role.USER).build());
    }

    @Test
    @DisplayName("findByTokenHash: 존재하는 토큰이면 회원 정보와 함께 조회된다")
    void findByTokenHash_whenTokenExists_returnsTokenWithUser() {
        PasswordResetToken saved = tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("token-abc")
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());
        em.flush();
        em.clear();

        Optional<PasswordResetToken> found = tokenRepository.findByTokenHash("token-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getEmail()).isEqualTo("tester@example.com");
    }

    @Test
    @DisplayName("deleteByUserId: 이 회원의 토큰만 지우고 다른 회원 것은 남긴다")
    void deleteByUserId_deletesOnlyTokensOfGivenUser() {
        User other = userRepository.save(
                User.builder().name("other").email("other@example.com").password("pw").role(Role.USER).build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("mine").user(user).expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("others").user(other).expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        em.flush();

        tokenRepository.deleteByUserId(user.getId());

        assertThat(tokenRepository.findByTokenHash("mine")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("others")).isPresent();
    }

    @Test
    @DisplayName("deleteByExpiresAtBefore: 만료된 토큰만 지우고 지운 개수를 돌려준다")
    void deleteByExpiresAtBefore_deletesOnlyExpiredTokensAndReturnsCount() {
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("expired").user(user).expiresAt(LocalDateTime.now().minusMinutes(1)).build());
        tokenRepository.save(PasswordResetToken.builder()
                .tokenHash("valid").user(user).expiresAt(LocalDateTime.now().plusMinutes(30)).build());
        em.flush();

        int deleted = tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        assertThat(deleted).isEqualTo(1);
        assertThat(tokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(tokenRepository.findByTokenHash("valid")).isPresent();
    }
}
