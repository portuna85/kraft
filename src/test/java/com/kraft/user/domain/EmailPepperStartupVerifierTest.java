package com.kraft.user.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 기동 검증이 pepper 불일치를 잡고 일치하면 통과시키는지. pepper 상태는 정적이라 테스트마다 원래 값으로 되돌린다. */
@DataJpaTest
class EmailPepperStartupVerifierTest {

    private static final String PEPPER = "correct-pepper-0123456789";

    @Autowired
    private UserRepository userRepository;

    private EmailPepperStartupVerifier verifier;

    @BeforeEach
    void setUp() {
        // 운영에서는 속성이 켜졌을 때만 빈이 되므로(ConditionalOnProperty) 슬라이스에서는 직접 만든다.
        verifier = new EmailPepperStartupVerifier(userRepository);
        EmailHasher.configurePepper(PEPPER);
        userRepository.save(User.builder().name("verifier-a").email("a@example.com").password("x").role(Role.USER).build());
        userRepository.save(User.builder().name("verifier-b").email("b@example.com").password("x").role(Role.USER).build());
        userRepository.flush();
    }

    @AfterEach
    void tearDown() {
        EmailHasher.configurePepper(PEPPER);
    }

    @Test
    @DisplayName("저장된 해시를 만든 pepper와 같으면 통과한다")
    void sameInstance_passes() {
        assertThatCode(() -> verifier.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 pepper로 기동하면 이메일은 복호화돼도 기동을 실패시킨다")
    void differentPepper_failsStartup() {
        EmailHasher.configurePepper("another-pepper-0123456789");

        assertThatThrownBy(() -> verifier.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_HASH_PEPPER");
    }

    @Test
    @DisplayName("pepper가 설정되지 않은 환경(최소 슬라이스·rekey)에서는 아무것도 하지 않는다")
    void withoutPepper_isNoop() {
        EmailHasher.configurePepper("");

        assertThatCode(() -> verifier.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계정이 하나도 없으면 통과한다(신규 환경)")
    void emptyDatabase_passes() {
        userRepository.deleteAll();
        userRepository.flush();

        assertThatCode(() -> verifier.run(null)).doesNotThrowAnyException();
    }
}
