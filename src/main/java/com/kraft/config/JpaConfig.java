package com.kraft.config;

import com.kraft.config.auth.dto.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * JPA Auditing 설정
 * - 생성일시/수정일시 자동 관리
 * - 생성자/수정자 자동 추적 (세션 기반)
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    /**
     * 현재 사용자 정보를 제공하는 AuditorAware 구현
     * 세션에서 사용자 정보를 추출하여 반환
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            try {
                ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes == null) {
                    return Optional.of("system");
                }

                HttpSession session = attributes.getRequest().getSession(false);
                if (session == null) {
                    return Optional.of("anonymous");
                }

                SessionUser user = (SessionUser) session.getAttribute("user");
                return Optional.ofNullable(user)
                        .map(SessionUser::name)
                        .or(() -> Optional.of("anonymous"));
            } catch (Exception e) {
                // 테스트 환경이나 비동기 작업에서는 RequestContext가 없을 수 있음
                return Optional.of("system");
            }
        };
    }
}