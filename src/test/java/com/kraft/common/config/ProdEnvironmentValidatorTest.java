package com.kraft.common.config;

import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("운영 환경 검증기 단위 테스트")
class ProdEnvironmentValidatorTest {

    @Test
    @DisplayName("필수 환경변수가 모두 있으면 누락 목록이 비어야 한다")
    void missingRequiredVariables_returnsEmptyWhenAllPresent() throws Exception {
        ProdEnvironmentValidator validator = validator(
                "https://kraft.example.com",
                "https://example.com/lotto?round=%d",
                "secret",
                "ops-token"
        );

        assertThat(validator.missingRequiredVariables()).isEmpty();
    }

    @Test
    @DisplayName("비어 있는 운영 필수값만 누락 목록에 포함해야 한다")
    void missingRequiredVariables_returnsOnlyMissingKeys() throws Exception {
        ProdEnvironmentValidator validator = validator(
                "",
                " ",
                null,
                ""
        );

        assertThat(validator.missingRequiredVariables()).containsExactly(
                "KRAFT_PUBLIC_BASE_URL",
                "KRAFT_EXTERNAL_LOTTO_URL_TEMPLATE",
                "KRAFT_REVALIDATE_SECRET",
                "KRAFT_OPS_TOKEN"
        );
    }

    @Test
    @DisplayName("운영 환경에서 공개 기준 주소가 설정되면 교차 출처 설정이 전체 허용이 아니어야 한다")
    void corsOrigin_neverWildcardInProd() throws Exception {
        // prod에서 ProdEnvironmentValidator가 publicBaseUrl 필수를 강제하므로
        // CorsConfig.resolvedOrigins()는 반드시 명시적 URL을 반환해야 한다.
        assertThat(new CorsConfig(new PublicBaseUrlProperties("https://kraft.io.kr")).resolvedOrigins())
                .doesNotContain("*");

        assertThat(new CorsConfig(new PublicBaseUrlProperties("")).resolvedOrigins())
                .containsExactly("*"); // 개발 환경에서만 허용
    }

    @Test
    @DisplayName("운영 환경에서 필수 환경변수가 없으면 시작 시 예외가 발생해야 한다")
    void validate_prod_throwsOnMissingVars() throws Exception {
        ProdEnvironmentValidator validator = validator("", "", "", "");
        // prod profile을 가진 MockEnvironment에서 validate() 호출
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.addActiveProfile("prod");
        Field envField = ProdEnvironmentValidator.class.getDeclaredField("environment");
        envField.setAccessible(true);
        envField.set(validator, prodEnv);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KRAFT_PUBLIC_BASE_URL");
    }

    private static ProdEnvironmentValidator validator(String publicBaseUrl,
                                                      String externalUrlTemplate,
                                                      String revalidateSecret,
                                                      String opsToken) throws Exception {
        ProdEnvironmentValidator validator = new ProdEnvironmentValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                new ExternalLottoProperties(externalUrlTemplate, "0 30 21 * * SAT",
                        "https://www.dhlottery.co.kr/lt645/result", "XMLHttpRequest"),
                new RevalidateProperties(revalidateSecret, "http://web:3000"),
                new OpsProperties(opsToken)
        );
        Field publicBaseUrlField = ProdEnvironmentValidator.class.getDeclaredField("publicBaseUrl");
        publicBaseUrlField.setAccessible(true);
        publicBaseUrlField.set(validator, publicBaseUrl);
        return validator;
    }
}
