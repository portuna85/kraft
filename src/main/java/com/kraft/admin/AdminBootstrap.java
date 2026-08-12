package com.kraft.admin;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time admin account bootstrap.
 *
 * Set KRAFT_ADMIN_BOOTSTRAP_USERNAME and KRAFT_ADMIN_BOOTSTRAP_PASSWORD before the
 * first startup (via env var or .env.local). The runner creates the account only when
 * the admin_users table is empty; subsequent restarts with the same values are silently
 * ignored.
 *
 * Remove both values from the config after the initial account is created.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Environment environment;
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrap(AdminUserRepository adminUserRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock,
                          Environment environment,
                          @Value("${KRAFT_ADMIN_BOOTSTRAP_USERNAME:}") String bootstrapUsername,
                          @Value("${KRAFT_ADMIN_BOOTSTRAP_PASSWORD:}") String bootstrapPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.environment = environment;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String username = bootstrapUsername;
        String password = bootstrapPassword;

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            // M-12: 부트스트랩 값이 없다는 것 자체는 정상(최초 부트스트랩 이후 값을 지운
            // steady-state의 의도된 모습)이다. 하지만 prod인데 admin_users까지 비어 있으면
            // 실수로 빈 DB에 배포됐거나 관리자 계정이 전부 사라진 상황을 아무도 모르고
            // 넘어갈 수 있다 — 앱 전체를 막지는 않되(공개 서비스는 관리자 로그인과 무관하게
            // 계속 동작해야 하므로) 배포 로그에서 바로 보이는 경고를 남긴다.
            if (isProdProfile() && adminUserRepository.count() == 0) {
                log.warn("AdminBootstrap: prod 프로파일인데 admin_users가 비어 있고 부트스트랩 값도 "
                        + "없습니다 — 관리자 로그인이 불가능한 상태입니다. 의도된 것이 아니라면 "
                        + "KRAFT_ADMIN_BOOTSTRAP_USERNAME/PASSWORD를 설정한 뒤 재배포하세요.");
            }
            return;
        }

        if (adminUserRepository.count() > 0) {
            log.info("AdminBootstrap: admin_users is not empty — skipping bootstrap.");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        adminUserRepository.save(new AdminUser(
                username.trim(),
                passwordEncoder.encode(password),
                "ROLE_ADMIN",
                now
        ));
        log.info("AdminBootstrap: initial admin account created.");
    }

    private boolean isProdProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }
}
