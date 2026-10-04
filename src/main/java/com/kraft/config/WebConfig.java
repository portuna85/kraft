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
                // 이름을 받는다) — 같은 URL이 다른 내용으로 바뀌는 일은 없다. 그래도 365일
                // immutable로 캐시하지 않는다(BE-14): 모더레이션으로 글·이미지를 지워도 브라우저와
                // 프록시 캐시에는 그 이미지가 최대 1년 남는다. 하루면 같은 방문자의 반복 요청은
                // 충분히 줄이면서, 지운 이미지가 남는 기간을 짧게 둔다. 365일 immutable은 빌드마다
                // 경로가 바뀌는 정적 산출물(/js, /css)에만 쓴다. SecurityConfig의 staticResourceChain이
                // no-store를 붙이지 않아야 이 값이 실제로 응답에 남는다.
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
    }
}
