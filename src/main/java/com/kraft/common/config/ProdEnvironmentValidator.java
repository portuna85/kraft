package com.kraft.common.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProdEnvironmentValidator {

    @Value("${kraft.public-base-url:}")
    private String publicBaseUrl;

    private final Environment environment;
    private final ExternalLottoProperties externalLottoProperties;
    private final RevalidateProperties revalidateProperties;
    private final OpsProperties opsProperties;

    public ProdEnvironmentValidator(Environment environment,
                                    ExternalLottoProperties externalLottoProperties,
                                    RevalidateProperties revalidateProperties,
                                    OpsProperties opsProperties) {
        this.environment = environment;
        this.externalLottoProperties = externalLottoProperties;
        this.revalidateProperties = revalidateProperties;
        this.opsProperties = opsProperties;
    }

    // TD-005: 배포 스크립트(validate-env.sh)와 동일한 최소 길이 기준. 두 값 모두 저장 없는
    // 단일 값 비교로 쓰이는 공유 비밀이라(OpsProperties/RevalidateProperties 참고), 짧은
    // 값은 무작위 대입에 취약하다.
    private static final int MIN_SECRET_LENGTH = 32;

    @PostConstruct
    void validate() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }

        List<String> missing = missingRequiredVariables();
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "prod profile requires the following environment variables to be set: " + missing);
        }

        List<String> weak = weakSecretVariables();
        if (!weak.isEmpty()) {
            throw new IllegalStateException(
                    "prod profile requires the following environment variables to be at least "
                            + MIN_SECRET_LENGTH + " characters long: " + weak);
        }
    }

    List<String> missingRequiredVariables() {
        List<String> missing = new ArrayList<>();
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            missing.add("KRAFT_PUBLIC_BASE_URL");
        }
        if (externalLottoProperties.urlTemplate() == null || externalLottoProperties.urlTemplate().isBlank()) {
            missing.add("KRAFT_EXTERNAL_LOTTO_URL_TEMPLATE");
        }
        if (revalidateProperties.secret() == null || revalidateProperties.secret().isBlank()) {
            missing.add("KRAFT_REVALIDATE_SECRET");
        }
        if (opsProperties.token() == null || opsProperties.token().isBlank()) {
            missing.add("KRAFT_OPS_TOKEN");
        }
        return missing;
    }

    List<String> weakSecretVariables() {
        List<String> weak = new ArrayList<>();
        if (revalidateProperties.secret() != null && revalidateProperties.secret().length() < MIN_SECRET_LENGTH) {
            weak.add("KRAFT_REVALIDATE_SECRET");
        }
        if (opsProperties.token() != null && opsProperties.token().length() < MIN_SECRET_LENGTH) {
            weak.add("KRAFT_OPS_TOKEN");
        }
        return weak;
    }
}
