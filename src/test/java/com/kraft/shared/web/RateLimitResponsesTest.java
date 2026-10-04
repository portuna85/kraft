package com.kraft.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitResponsesTest {

    @Test
    @DisplayName("429에 Retry-After 헤더와 code 확장 속성을 싣는다")
    void tooManyRequests_hasStatusHeaderAndCode() {
        ResponseEntity<ProblemDetail> response = RateLimitResponses.tooManyRequests("POST_WRITE_LIMIT", 42);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(429);
        assertThat(body.getProperties()).containsEntry("code", "POST_WRITE_LIMIT");
        assertThat(body.getDetail()).contains("잠시 후");
    }
}
