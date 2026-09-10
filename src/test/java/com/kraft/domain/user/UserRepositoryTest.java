package com.kraft.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link UserRepository} 통합 테스트. 실제 H2에 대해 실행되어, {@link User#email}이 AES로
 * 암호화되어 저장되고(EmailAttributeConverter), 조회·중복확인·유니크 제약은 SHA-512 해시를 담은
 * {@code email_hash} 컬럼(3.9절)을 통해 이뤄짐을 검증한다. {@code @DataJpaTest}는 JPA 관련 빈만
 * 스캔하므로 {@link EmailAttributeConverter}(Spring 빈으로 등록되어야 {@code @Value} 키 주입이
 * 됨)를 명시적으로 {@code @Import}해야 한다.
 */
@DataJpaTest
@Import(EmailAttributeConverter.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private static User user(String email) {
        return User.builder().name("tester").email(email).password("encoded").role(Role.USER).build();
    }

    @Test
    @DisplayName("findByEmailHash: 저장된 이메일의 해시면 회원을 조회한다")
    void findByEmailHash_존재하면_조회된다() {
        userRepository.save(user("found@example.com"));

        Optional<User> result = userRepository.findByEmailHash(EmailHasher.sha512Hex("found@example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("tester");
        assertThat(result.get().getEmail()).isEqualTo("found@example.com");
    }

    @Test
    @DisplayName("findByEmailHash: 존재하지 않는 이메일의 해시면 빈 Optional")
    void findByEmailHash_존재하지_않으면_빈값() {
        assertThat(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).isEmpty();
    }

    @Test
    @DisplayName("existsByEmailHash: 저장 여부에 따라 true/false")
    void existsByEmailHash_동작확인() {
        userRepository.save(user("exists@example.com"));

        assertThat(userRepository.existsByEmailHash(EmailHasher.sha512Hex("exists@example.com"))).isTrue();
        assertThat(userRepository.existsByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).isFalse();
    }

    @Test
    @DisplayName("existsByName: 저장 여부에 따라 true/false")
    void existsByName_동작확인() {
        userRepository.save(user("name-check@example.com"));

        assertThat(userRepository.existsByName("tester")).isTrue();
        assertThat(userRepository.existsByName("nobody")).isFalse();
    }

    @Test
    @DisplayName("email_hash 유니크 제약: 같은 이메일을 두 번 저장하면 DataIntegrityViolationException")
    void emailHash_유니크_제약이_실제로_동작한다() {
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
