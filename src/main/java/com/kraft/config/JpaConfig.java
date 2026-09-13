package com.kraft.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA 감사 필드(BaseEntity의 createdAt/updatedAt)와 주기 작업을 켠다.
 * {@code @EnableScheduling}은 미연결·삭제 예정 이미지 파일을 치우는
 * {@code PostImageCleaner}가 쓴다.
 */
@Configuration
@EnableJpaAuditing
@EnableScheduling
public class JpaConfig {
}
