package com.kraft.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA 감사 필드(BaseEntity의 createdAt/updatedAt)와 백그라운드 실행을 켠다.
 * <ul>
 * <li>{@code @EnableScheduling} — 이미지 정리(PostImageCleaner), 만료 토큰 정리(ExpiredTokenPurger),
 * 메일 발송(OutboxMailWorker)의 주기 작업</li>
 * <li>{@code @EnableAsync} — 가입·재발송 직후의 메일 발송을 요청 스레드와 분리한다.
 * SMTP를 기다리는 동안 DB 커넥션을 붙잡지 않기 위한 것이다.</li>
 * </ul>
 */
@Configuration
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class JpaConfig {
}
