package com.kraft.operations.hmacbackfill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** {@code hmac-backfill} 프로파일에서만 돌아 한 번 채우고 종료 코드(성공 0, 실패 1)를 남긴다. */
@Slf4j
@RequiredArgsConstructor
@Profile("hmac-backfill")
@Component
public class EmailHmacBackfillRunner implements ApplicationRunner {

    private final EmailHmacBackfillService service;
    private final ConfigurableApplicationContext context;

    @Value("${app.security.email-encryption-key}")
    private String encryptionKey;

    @Override
    public void run(ApplicationArguments args) {
        int exitCode;
        try {
            exitCode = service.backfill(encryptionKey).complete() ? 0 : 1;
        } catch (Exception e) {
            log.error("이메일 HMAC 백필에 실패했습니다.", e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
