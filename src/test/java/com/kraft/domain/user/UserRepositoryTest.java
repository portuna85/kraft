package com.kraft.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserRepository} 통합 테스트. 실제 H2에 대해 실행되어, {@link User}의
 * {@code email} 유니크 제약(3.3절)이 DB 레벨에서 실제로 동작하는지까지 검증한다.
 */
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private static User user(String email) {
        return User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
    }

    @Test
    @DisplayName("findByEmail: 저장된 이메일이면 회원을 조회한다")
    void findByEmail_존재하면_조회된다() {
        userRepository.save(user("found@example.com"));

        Optional<User> result = userRepository.findByEmail("found@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("tester");
    }

    @Test
    @DisplayName("findByEmail: 존재하지 않는 이메일이면 빈 Optional")
    void findByEmail_존재하지_않으면_빈값() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail: 저장 여부에 따라 true/false")
    void existsByEmail_동작확인() {
        userRepository.save(user("exists@example.com"));

        assertThat(userRepository.existsByEmail("exists@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("email 유니크 제약: 같은 이메일을 두 번 저장하면 DataIntegrityViolationException")
    void email_유니크_제약이_실제로_동작한다() {
        userRepository.saveAndFlush(user("dup@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장하면 IDENTITY 전략으로 ID가 채워진다 (감사 필드 검증은 PostRepositoryTest에서 함께 수행)")
    void 저장하면_ID가_채워진다() {
        User saved = userRepository.save(user("id-check@example.com"));

        assertThat(saved.getId()).isNotNull();
    }
}
