package com.kraft.post.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON을 {@code <script>} 안에 그대로 써도 안전한지 검증한다(개선 보고서 "JSON을 HTML script에
 * 넣는 경계 검증 부족"). 실제 공격 재현은 {@code PostPageController}를 통한 렌더링 테스트와
 * e2e가 맡고, 여기서는 변환 함수 자체의 계약(탈출 문자열을 남기지 않는다 / 원문을 그대로
 * 복원할 수 있다)을 고정한다.
 */
class JsonHtmlEmbeddingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void escapedOutputContainsNoScriptClosingSequence() {
        String malicious = "</script><script>alert(1)</script>";
        String json = objectMapper.writeValueAsString(malicious);

        String escaped = JsonHtmlEmbedding.escapeForHtmlScript(json);

        assertThat(escaped).doesNotContainIgnoringCase("</script");
        assertThat(escaped).doesNotContain("<").doesNotContain(">").doesNotContain("&");
    }

    @Test
    void escapedOutputStillParsesBackToTheOriginalText() {
        String original = "제목 </script> & <b>강조</b> 그대로";
        String json = objectMapper.writeValueAsString(original);

        String escaped = JsonHtmlEmbedding.escapeForHtmlScript(json);
        String parsedBack = objectMapper.readValue(escaped, String.class);

        assertThat(parsedBack).isEqualTo(original);
    }

    @Test
    void lineAndParagraphSeparatorsAreEscapedToo() {
        String withSeparators = "줄바꿈 문단 구분";
        String json = objectMapper.writeValueAsString(withSeparators);

        String escaped = JsonHtmlEmbedding.escapeForHtmlScript(json);

        assertThat(escaped).doesNotContain(" ").doesNotContain(" ");
        assertThat(objectMapper.readValue(escaped, String.class)).isEqualTo(withSeparators);
    }

    @Test
    void ampersandIsEscapedFirstSoItDoesNotCorruptOtherEscapes() {
        String json = objectMapper.writeValueAsString("<>&");

        String escaped = JsonHtmlEmbedding.escapeForHtmlScript(json);

        // "&"를 나중에 치환했다면 "<" 안의 문자들이 다시 걸릴 일은 없지만, 역순으로
        // 만들다 실수하면 "&"의 "u0026" 부분이 다른 규칙에 또 걸릴 수 있다 — 순서 계약을
        // 명시적으로 고정해 둔다.
        assertThat(objectMapper.readValue(escaped, String.class)).isEqualTo("<>&");
    }
}
