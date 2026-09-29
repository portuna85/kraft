package com.kraft.user.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 이메일 HMAC pepper를 기동 시 {@link EmailHasher}에 심는다. 값은 로그에 남기지 않는다. */
@Component
public class EmailHmacConfiguration {

    private final String pepper;

    public EmailHmacConfiguration(@Value("${app.security.email-hash-pepper:}") String pepper) {
        this.pepper = pepper;
    }

    @PostConstruct
    void configure() {
        EmailHasher.configurePepper(pepper);
    }
}
