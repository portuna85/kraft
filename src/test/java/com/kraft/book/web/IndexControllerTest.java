package com.kraft.book.web;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IndexControllerTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void 메인페이지_로딩() {
        String body = rest.getForObject("/", String.class);
        assertThat(body).contains("<h2>게시글 목록</h2>");
        assertThat(body).contains("/posts/save");
        assertThat(body).contains("/oauth2/authorization/google");
        assertThat(body).contains("/oauth2/authorization/naver");
    }
}
