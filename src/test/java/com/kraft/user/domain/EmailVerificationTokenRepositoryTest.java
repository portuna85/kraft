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
    @DisplayName("findByToken: 존재하는 토큰이면 회원 정보와 함께 조회된다")
    void findByToken_whenTokenExists_returnsTokenWithUser() {
        EmailVerificationToken saved = tokenRepository.save(EmailVerificationToken.builder()
                .token("token-abc")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build());
        em.flush();
        em.clear();

        Optional<EmailVerificationToken> found = tokenRepository.findByToken("token-abc");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUser().getEmail()).isEqualTo("tester@example.com");
    }

    @Test
    @DisplayName("findByToken: 존재하지 않는 토큰이면 빈 Optional을 반환한다")
    void findByToken_whenTokenDoesNotExist_returnsEmptyOptional() {
        Optional<EmailVerificationToken> found = tokenRepository.findByToken("no-such-token");

        assertThat(found).isEmpty();
    }
}
