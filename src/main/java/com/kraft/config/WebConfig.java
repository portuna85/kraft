package com.kraft.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * {@code app.upload.dir}에 저장된 게시글 업로드 이미지를 {@code /images/**}로 서빙한다.
 * 이 경로는 {@code SecurityConfig}에 이미 permitAll로 등록돼 있어 별도 보안 설정이 필요 없다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @PostConstruct
    void ensureUploadDirExists() {
        try {
            Files.createDirectories(Path.of(uploadDir));
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 디렉터리를 생성할 수 없습니다: " + uploadDir, e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations(location)
                // 업로드 파일명은 PostImageService가 매번 새 UUID로 짓는다(교체할 파일도 새
                // 이름을 받는다) — 같은 URL이 다른 내용으로 바뀌는 일이 없으므로 길게 캐시해도
                // 안전하다. SecurityConfig의 staticResourceChain이 no-store를 붙이지 않아야
                // 이 값이 실제로 응답에 남는다. immutable()을 붙여(개선 보고서 BE-26) 브라우저가
                // 만료 전 재검증(If-None-Match 등) 요청조차 보내지 않게 한다 — 내용이 절대
                // 바뀌지 않는 URL이므로 재검증 왕복 자체가 낭비다.
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
