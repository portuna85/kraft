package com.kraft.post.markdown;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownParser}가 {@code src/vue/shared/markdown.js}(Vue 클라이언트 렌더링이
 * 쓰는 파서)와 같은 결과를 내는지 확인한다(13단계, 본문 SSR).
 * <p>
 * {@code src/test/resources/markdown/cases.json} 공용 픽스처를 이 테스트와
 * {@code src/vue/shared/markdown-cases.test.js}(node --test, {@code npm run test:unit})가
 * 각자의 JSON 파서로 읽어 같은 입력에 대해 같은 AST를 기대한다 — 로직을 한쪽만 고치면 두
 * 테스트 중 하나가 반드시 깨진다.
 */
class MarkdownParserTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TestFactory
    Stream<DynamicTest> 공용_픽스처의_모든_사례를_JS와_같은_AST로_해석한다() throws IOException {
        List<Map<String, Object>> cases = loadFixture();
        return cases.stream().map(testCase -> {
            String name = (String) testCase.get("name");
            String input = (String) testCase.get("input");
            Object expected = testCase.get("expected");
            return DynamicTest.dynamicTest(name, () ->
                    assertThat(MarkdownParser.parse(input)).isEqualTo(expected));
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> loadFixture() throws IOException {
        Path path = Path.of("src/test/resources/markdown/cases.json");
        try (InputStream in = Files.newInputStream(path)) {
            return OBJECT_MAPPER.readValue(in, List.class);
        }
    }

    // 픽스처가 다루지 않는, 자바 쪽 경계 조건(문자열 인덱스 범위 등)만 따로 확인한다.

    @org.junit.jupiter.api.Test
    void null_입력은_빈_목록이다() {
        assertThat(MarkdownParser.parse(null)).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void 여는_기호로_끝나는_문자열도_예외_없이_글자_그대로_남는다() {
        assertThat(MarkdownParser.parse("문장 끝에 [")).isEqualTo(
                List.of(Map.of("type", "p", "children", List.of("문장 끝에 ["))));
    }

    @org.junit.jupiter.api.Test
    void 백틱_하나만_있으면_코드로_해석하지_않는다() {
        assertThat(MarkdownParser.parse("`닫히지 않은 코드")).isEqualTo(
                List.of(Map.of("type", "p", "children", List.of("`닫히지 않은 코드"))));
    }

    /**
     * 본문은 10,000자로 제한된다(ContentPolicy). 그 한도 안에서 닫히지 않는 구분자를 잔뜩 넣어도
     * 상세 화면 SSR이 눈에 띄게 느려지지 않는지(P2-3) 최악 입력으로 고정한다 — 파서는 구분자마다
     * 닫는 위치를 다시 찾으므로 입력 길이의 제곱에 비례하지만, 한도 안에서는 무시할 만하다.
     */
    @org.junit.jupiter.api.Test
    void worstCaseInputsWithinContentLimit_parseQuickly() {
        int limit = com.kraft.shared.domain.ContentPolicy.POST_CONTENT_MAX_LENGTH;
        for (String unit : List.of("[", "*", "**a ", "`", "[a](", "*a* ", "**a*b")) {
            String source = unit.repeat(limit / unit.length());

            long start = System.nanoTime();
            MarkdownParser.parse(source);
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertThat(millis).as("입력 '%s' 반복 파싱 시간(ms)", unit).isLessThan(2_000);
        }
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("닫히지 않은 기호가 아주 많아도 선형 시간에 끝난다")
    void unclosedDelimiters_doNotBlowUp() {
        for (String unit : List.of("[", "`", "*", "**", "[a](")) {
            String input = unit.repeat(10_000 / unit.length());

            assertThat(org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    java.time.Duration.ofSeconds(2), () -> MarkdownParser.parse(input))).isNotNull();
        }
    }
}
