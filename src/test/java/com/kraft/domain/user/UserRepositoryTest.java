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

    private static User user(String name, String email) {
        return User.builder().name(name).email(email).password("encoded").role(Role.USER).build();
    }

    @Test
    @DisplayName("findByEmailHash: 저장된 이메일의 해시면 회원을 조회한다")
    void findByEmailHash_whenEmailHashExists_returnsUser() {
        userRepository.save(user("found@example.com"));

        Optional<User> result = userRepository.findByEmailHash(EmailHasher.sha512Hex("found@example.com"));

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("tester");
        assertThat(result.get().getEmail()).isEqualTo("found@example.com");
    }

    @Test
    @DisplayName("findByEmailHash: 존재하지 않는 이메일의 해시면 빈 Optional")
    void findByEmailHash_whenEmailHashDoesNotExist_returnsEmptyOptional() {
        assertThat(userRepository.findByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).isEmpty();
    }

    @Test
    @DisplayName("existsByEmailHash: 저장 여부에 따라 true/false")
    void existsByEmailHash_returnsCorrectBooleanBasedOnExistence() {
        userRepository.save(user("exists@example.com"));

        assertThat(userRepository.existsByEmailHash(EmailHasher.sha512Hex("exists@example.com"))).isTrue();
        assertThat(userRepository.existsByEmailHash(EmailHasher.sha512Hex("nobody@example.com"))).isFalse();
    }

    @Test
    @DisplayName("existsByName: 저장 여부에 따라 true/false")
    void existsByName_returnsCorrectBooleanBasedOnExistence() {
        userRepository.save(user("name-check@example.com"));

        assertThat(userRepository.existsByName("tester")).isTrue();
        assertThat(userRepository.existsByName("nobody")).isFalse();
    }

    @Test
    @DisplayName("email_hash 유니크 제약: 같은 이메일을 두 번 저장하면 DataIntegrityViolationException")
    void save_duplicateEmail_violatesUniqueConstraint() {
        // 이름은 서로 다르게 둔다 — 이름 유니크 제약이 먼저 걸려 이메일 제약을 가리지 않도록.
        userRepository.saveAndFlush(user("first", "dup@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("second", "dup@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("name 유니크 제약: 이메일이 달라도 같은 이름은 두 번 저장되지 않는다")
    void save_duplicateName_violatesUniqueConstraint() {
        // existsByName() 사전 검사와 INSERT 사이의 경쟁은 DB 제약만이 막을 수 있다. 예전에는
        // 이 제약이 없어 서로 다른 이메일이 같은 이름을 갖는 상태가 실제로 저장됐다(F09).
        userRepository.saveAndFlush(user("같은닉네임", "one@example.com"));

        assertThatThrownBy(() -> userRepository.saveAndFlush(user("같은닉네임", "two@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장하면 IDENTITY 전략으로 ID가 채워진다 (감사 필드 검증은 PostRepositoryTest에서 함께 수행)")
    void save_assignsGeneratedId() {
        User saved = userRepository.save(user("id-check@example.com"));

        assertThat(saved.getId()).isNotNull();
    }
}
