package com.kraft.post.web;

/**
 * JSON 문자열을 {@code <script type="application/json">} 안에 안전하게 넣기 위한 변환.
 * <p>
 * Jackson은 JSON 문자열 규칙에 맞춰 {@code "}, {@code \}만 이스케이프하고 {@code <}, {@code >},
 * {@code &}는 그대로 둔다 — JSON으로는 안전하지만 HTML로는 아니다. 사용자가 쓴 제목·본문·댓글이
 * 그대로 이 JSON에 들어가는데, {@code th:utext}는 이스케이핑을 전혀 하지 않으므로 값에
 * {@code </script>}가 포함되면 브라우저가 HTML을 파싱하는 시점에 그 자리에서 script 요소를
 * 끝내 버린다. 그 뒤로 임의의 HTML·스크립트를 그대로 주입할 수 있다(저장형 XSS).
 * <p>
 * 여기서 만든 이스케이프는 {@link com.kraft.post.web.PostPageController}가 JSON을 페이지에
 * 끼워 넣는 지점에서만 쓴다 — REST API 응답을 만드는 공용 {@code ObjectMapper} 설정 자체를
 * 바꾸지 않는다. API 응답은 애초에 HTML에 박히지 않으므로 이런 이스케이프가 필요 없고,
 * 전역으로 걸면 불필요한 {@code <} 같은 표기가 API JSON에도 섞여 나간다.
 */
final class JsonHtmlEmbedding {

    private JsonHtmlEmbedding() {
    }

    /**
     * HTML {@code <script>} 안에 그대로 써도 안전하도록 JSON 문자열을 변환한다.
     * <p>
     * {@code <}, {@code >}, {@code &}를 유니코드 이스케이프로 바꿔 {@code </script>}·HTML
     * 주석·엔티티 참조로 해석될 여지를 없앤다. U+2028·U+2029(JS 줄바꿈 구분자)도 함께 바꾼다 —
     * 지금 소비하는 쪽은 전부 {@code JSON.parse}를 쓰지만, 방어적으로 eval류 파싱에도 안전하게
     * 남겨 둔다. 순서가 중요하다 — {@code &}를 가장 먼저 바꿔야, 뒤에서 넣은 {@code <} 같은
     * 이스케이프 표기 안의 문자가 다시 치환되지 않는다.
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
