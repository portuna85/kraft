package com.kraft.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("사용자를 저장하고 조회할 수 있다")
    void saveAndFind() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");

        // when
        User saved = userRepository.save(user);

        // then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("testuser");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("이름으로 사용자를 찾을 수 있다")
    void findByName() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        userRepository.save(user);

        // when
        Optional<User> found = userRepository.findByName("testuser");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("존재하지 않는 이름으로 조회하면 empty를 반환한다")
    void findByNameNotFound() {
        // when
        Optional<User> found = userRepository.findByName("notexist");

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("이름 중복 여부를 확인할 수 있다")
    void existsByName() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        userRepository.save(user);

        // expect
        assertThat(userRepository.existsByName("testuser")).isTrue();
        assertThat(userRepository.existsByName("otheruser")).isFalse();
    }

    @Test
    @DisplayName("이메일 중복 여부를 확인할 수 있다")
    void existsByEmail() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        userRepository.save(user);

        // expect
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
    }

    @Test
    @DisplayName("자신을 제외한 이메일 중복 여부를 확인할 수 있다")
    void existsByEmailAndIdNot() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        User saved = userRepository.save(user);

        // expect
        assertThat(userRepository.existsByEmailAndIdNot("test@example.com", saved.getId())).isFalse();
        assertThat(userRepository.existsByEmailAndIdNot("test@example.com", saved.getId() + 1)).isTrue();
    }

    @Test
    @DisplayName("같은 이름으로 중복 저장하면 예외가 발생한다")
    void nameMustBeUnique() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        userRepository.save(user);

        // expect
        assertThatThrownBy(() -> {
            User duplicate = User.of("testuser", "encoded456", "another@example.com");
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 이메일로 중복 저장하면 예외가 발생한다")
    void emailMustBeUnique() {
        // given
        User user = User.of("testuser", "encoded123", "test@example.com");
        userRepository.save(user);

        // expect
        assertThatThrownBy(() -> {
            User duplicate = User.of("anotheruser", "encoded456", "test@example.com");
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}

