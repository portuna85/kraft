package com.kraft.shared.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionCheckTest {

    @Test
    @DisplayName("버전이 같으면 통과한다")
    void sameVersion_passes() {
        assertThatCode(() -> VersionCheck.require(String.class, 1L, 3L, 3L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("기대 버전이 null이면(API를 거치지 않는 내부 호출) 검사하지 않는다")
    void nullExpected_isNotChecked() {
        assertThatCode(() -> VersionCheck.require(String.class, 1L, 3L, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("버전이 다르면 낙관적 락 예외를 던진다")
    void differentVersion_throwsOptimisticLockException() {
        assertThatThrownBy(() -> VersionCheck.require(String.class, 7L, 3L, 2L))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class)
                .satisfies(e -> assertThat(((ObjectOptimisticLockingFailureException) e).getIdentifier()).isEqualTo(7L));
    }

    @Test
    @DisplayName("버전이 다르면 If-Match 불일치 예외(PreconditionFailedException)이고, 낙관적 락 예외의 하위 타입이다")
    void differentVersion_throwsPreconditionFailed() {
        assertThatThrownBy(() -> VersionCheck.require(String.class, 7L, 3L, 2L))
                .isInstanceOf(PreconditionFailedException.class);
    }
}
