package com.kraft.post.web;

/**
 * JSON을 {@code <script type="application/json">} 안에 안전하게 넣기 위한 변환. Jackson은 {@code <}·{@code >}·{@code &}를
 * 이스케이프하지 않아, 값에 {@code </script>}가 있으면 {@code th:utext}가 그대로 내보내 script 요소가 끝나고 임의의 HTML이
 * 주입된다(저장형 XSS). 페이지에 끼워 넣는 지점({@link com.kraft.post.web.PostPageController})에서만 쓰고, REST 응답의
 * 공용 {@code ObjectMapper}는 바꾸지 않는다.
 */
final class JsonHtmlEmbedding {

    private JsonHtmlEmbedding() {
    }

    /** 값을 JSON으로 직렬화하고 {@link #escapeForHtmlScript}를 거쳐 모델에 담는다. {@code th:utext}로 내보낼 JSON은 이 메서드로만 넣는다(하나만 잊어도 저장형 XSS). */
    static void put(org.springframework.ui.Model model, String attributeName,
                    tools.jackson.databind.ObjectMapper objectMapper, Object value) {
        model.addAttribute(attributeName, escapeForHtmlScript(objectMapper.writeValueAsString(value)));
    }

    /**
     * JSON 문자열을 HTML {@code <script>} 안에 써도 안전하게 바꾼다. {@code <}·{@code >}·{@code &}와 U+2028·U+2029를
     * 유니코드 이스케이프로 바꾼다. {@code &}를 가장 먼저 바꿔야 뒤에서 넣은 이스케이프 안의 문자가 다시 치환되지 않는다.
     */
    static String escapeForHtmlScript(String json) {
        return json
                .replace("&", "\\u0026")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace(" ", "\\u2028")
                .replace(" ", "\\u2029");
    }
}
