package com.kraft.admin;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
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
    private final String bootstrapUsername;
    private final String bootstrapPassword;

    public AdminBootstrap(AdminUserRepository adminUserRepository,
                          PasswordEncoder passwordEncoder,
                          Clock clock,
                          @Value("${KRAFT_ADMIN_BOOTSTRAP_USERNAME:}") String bootstrapUsername,
                          @Value("${KRAFT_ADMIN_BOOTSTRAP_PASSWORD:}") String bootstrapPassword) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        String username = bootstrapUsername;
        String password = bootstrapPassword;

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
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
}
