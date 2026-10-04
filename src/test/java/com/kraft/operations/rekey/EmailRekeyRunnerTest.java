package com.kraft.operations.rekey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 키 교체 도구의 종료 코드. 0이면 전 계정이 새 키로 검증됐고, 1이면 DB를 백업 시점으로 되돌려야 한다. */
class EmailRekeyRunnerTest {

    private final EmailRekeyService service = mock(EmailRekeyService.class);

    private EmailRekeyRunner runner() {
        EmailRekeyRunner runner = new EmailRekeyRunner(service, mock(ConfigurableApplicationContext.class));
        ReflectionTestUtils.setField(runner, "oldKey", "old-key");
        ReflectionTestUtils.setField(runner, "newKey", "new-key");
        return runner;
    }

    @Test
    @DisplayName("전부 변환되고 검증이 모두 일치하면 종료 코드 0")
    void allVerified_exitsZero() {
        given(service.rekeyAll("old-key", "new-key")).willReturn(new EmailRekeyService.Result(3, 0));
        given(service.verifyAll("new-key")).willReturn(new EmailRekeyService.VerifyResult(3, 0));

        assertThat(runner().rekey()).isZero();
    }

    @Test
    @DisplayName("검증에서 불일치가 하나라도 있으면 종료 코드 1")
    void verificationMismatch_exitsOne() {
        given(service.rekeyAll("old-key", "new-key")).willReturn(new EmailRekeyService.Result(3, 0));
        given(service.verifyAll("new-key")).willReturn(new EmailRekeyService.VerifyResult(2, 1));

        assertThat(runner().rekey()).isEqualTo(1);
    }

    @Test
    @DisplayName("변환 중 예외가 나면 종료 코드 1이고 검증은 시작하지 않는다")
    void rekeyFailure_exitsOne_andSkipsVerification() {
        given(service.rekeyAll("old-key", "new-key"))
                .willThrow(new IllegalStateException("어느 키로도 복호화되지 않는다. userId=7"));

        assertThat(runner().rekey()).isEqualTo(1);
        verify(service, never()).verifyAll("new-key");
    }
}
