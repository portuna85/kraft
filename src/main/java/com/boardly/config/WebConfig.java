package com.boardly.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.context.annotation.Bean;

@Configuration
public class WebConfig {
    @Bean
    public HiddenHttpMethodFilter hiddenHttpMethodFilter() { return new HiddenHttpMethodFilter(); }
    // UTF-8은 Boot 기본이지만, Nginx/톰캣 앞단 환경이라면 CharacterEncodingFilter를 추가하세요.
}
