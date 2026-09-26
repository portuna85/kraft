package com.kraft.post.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 가벼운 마크다운(12단계)의 서버 측 파서. {@code src/vue/shared/markdown.js}의
 * {@code parseMarkdown}을 그대로 자바로 옮긴 것이다 — 상세 화면은 Vue가 마운트하기 전(또는
 * 마운트에 실패했을 때) 서버가 먼저 본문을 그리는데(post-update.html의 post-ssr), 지금까지는
 * 이 자리에 원문을 그대로 찍어 {@code **굵게**} 같은 문법이 그대로 보였다(13단계에서 고침).
 * <p>
 * 결과 AST는 자바 레코드가 아니라 {@link Map}·{@link List}·{@link String}만으로 이루어진
 * JSON과 같은 모양이다(markdown.js가 만드는 것과 같은 구조 — 블록은
 * {@code {type, children|items}}, 인라인은 문자열 그대로거나
 * {@code {type, text|children|href}}). 이렇게 두면
 * <ul>
 *   <li>Thymeleaf 프래그먼트(layout/markdown.html)가 맵 접근({@code node.type})만으로
 *       그릴 수 있고,</li>
 *   <li>{@code src/test/resources/markdown/cases.json} 공용 픽스처를 자바·JS 양쪽 테스트가
 *       각자의 JSON 파서로 읽어 이 메서드의 결과와 그대로 {@code equals()} 비교할 수 있다
 *       (별도의 JSON↔레코드 매핑 코드가 필요 없다).</li>
 * </ul>
 * 두 구현이 갈라지면 SSR과 Vue가 마운트한 뒤의 모양이 달라지므로, 로직을 바꿀 때는 반드시
 * 두 파일을 같이 고치고 공용 픽스처로 양쪽 테스트가 같은 결과를 내는지 함께 확인한다
 * (MarkdownParserTest, markdown.test.js).
 * <p>
 * 지원 문법과 규칙은 markdown.js 상단 주석과 동일하다: 문단·글머리 목록·번호 목록,
 * {@code **굵게**}·{@code *기울임*}·{@code `코드`}·{@code [글자](주소)}(http/https만 링크,
 * 나머지 스킴은 평문). HTML 태그는 전혀 해석하지 않고 글자 그대로 남긴다 — 렌더링하는 쪽
 * (layout/markdown.html)이 {@code th:text}만 쓰므로 별도 이스케이프 없이도 안전하다.
 */
public final class MarkdownParser {

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^[-*]\\s+");
    private static final Pattern ORDERED_ITEM_PATTERN = Pattern.compile("^\\d+\\.\\s+");
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);

    private MarkdownParser() {
    }

    /** @return 블록 목록. 각 블록은 {@code Map<String, Object>}({@code type}에 따라 {@code children} 또는 {@code items}). */
    public static List<Object> parse(String source) {
        String normalized = (source == null ? "" : source).replace("\r\n", "\n");
        String[] lines = normalized.split("\n", -1);

        List<Object> blocks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                flush(blocks, current);
            } else {
                current.add(line);
            }
        }
        flush(blocks, current);
        return blocks;
    }

    private static void flush(List<Object> blocks, List<String> current) {
        if (!current.isEmpty()) {
            blocks.add(blockToNode(current));
            current.clear();
        }
    }

    private static Map<String, Object> blockToNode(List<String> lines) {
        if (lines.stream().allMatch(line -> LIST_ITEM_PATTERN.matcher(line).find())) {
            List<Object> items = lines.stream()
                    .map(line -> (Object) parseInline(LIST_ITEM_PATTERN.matcher(line).replaceFirst("")))
                    .toList();
            return node("ul", "items", items);
        }
        if (lines.stream().allMatch(line -> ORDERED_ITEM_PATTERN.matcher(line).find())) {
            List<Object> items = lines.stream()
                    .map(line -> (Object) parseInline(ORDERED_ITEM_PATTERN.matcher(line).replaceFirst("")))
                    .toList();
            return node("ol", "items", items);
        }
        return node("p", "children", parseInline(String.join("\n", lines)));
    }

    private static List<Object> parseInline(String text) {
        List<Object> nodes = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        int i = 0;
        int length = text.length();
        while (i < length) {
            char ch = text.charAt(i);

            if (ch == '\n') {
                flushBuffer(nodes, buffer);
                nodes.add(node("br"));
                i += 1;
                continue;
            }

            if (ch == '`') {
                int end = text.indexOf('`', i + 1);
                if (end != -1 && end > i + 1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("code", "text", text.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }

            if (text.startsWith("**", i)) {
                int end = findDelimiterEnd(text, i, "**", false);
                if (end != -1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("strong", "children", parseInline(text.substring(i + 2, end))));
                    i = end + 2;
                    continue;
                }
            }

            if (ch == '*') {
                // 단일 *만 숫자 경계를 추가로 막는다("5*3*2" 같은 곱셈 표기 오인 방지).
                int end = findDelimiterEnd(text, i, "*", true);
                if (end != -1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("em", "children", parseInline(text.substring(i + 1, end))));
                    i = end + 1;
                    continue;
                }
            }

            if (ch == '[') {
                LinkMatch link = tryParseLink(text, i);
                if (link != null) {
                    flushBuffer(nodes, buffer);
                    nodes.add(link.node());
                    i = link.nextIndex();
                    continue;
                }
            }

            buffer.append(ch);
            i += 1;
        }
        flushBuffer(nodes, buffer);
        return nodes;
    }

    private static void flushBuffer(List<Object> nodes, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            nodes.add(buffer.toString());
            buffer.setLength(0);
        }
    }

    /**
     * {@code **}·{@code *} 강조 구간의 닫는 위치를 찾는다. 조건을 만족하지 않으면
     * -1(글자 그대로 취급).
     */
    private static int findDelimiterEnd(String text, int start, String delimiter, boolean restrictDigitBoundary) {
        if (restrictDigitBoundary && isDigit(charAtOrNull(text, start - 1))) {
            return -1;
        }
        int end = text.indexOf(delimiter, start + delimiter.length());
        if (end == -1) {
            return -1;
        }
        String inner = text.substring(start + delimiter.length(), end);
        if (inner.isEmpty() || Character.isWhitespace(inner.charAt(0))
                || Character.isWhitespace(inner.charAt(inner.length() - 1))) {
            return -1;
        }
        if (restrictDigitBoundary && isDigit(charAtOrNull(text, end + delimiter.length()))) {
            return -1;
        }
        return end;
    }

    private static Character charAtOrNull(String text, int index) {
        return index >= 0 && index < text.length() ? text.charAt(index) : null;
    }

    private static boolean isDigit(Character ch) {
        return ch != null && ch >= '0' && ch <= '9';
    }

    /**
     * {@code [글자](주소)} 형태를 시도한다. http/https가 아니면 링크로 만들지 않고 null을
     * 돌려준다.
     */
    private static LinkMatch tryParseLink(String text, int start) {
        int closeBracket = text.indexOf(']', start + 1);
        if (closeBracket == -1 || closeBracket + 1 >= text.length() || text.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int closeParen = text.indexOf(')', closeBracket + 2);
        if (closeParen == -1) {
            return null;
        }
        String label = text.substring(start + 1, closeBracket);
        String url = text.substring(closeBracket + 2, closeParen).trim();
        if (!HTTP_URL_PATTERN.matcher(url).find()) {
            return null;
        }
        Map<String, Object> linkNode = node("a", "href", url);
        linkNode.put("children", parseInline(label));
        return new LinkMatch(linkNode, closeParen + 1);
    }

    private static Map<String, Object> node(String type) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type);
        return map;
    }

    private static Map<String, Object> node(String type, String key, Object value) {
        Map<String, Object> map = node(type);
        map.put(key, value);
        return map;
    }

    private record LinkMatch(Map<String, Object> node, int nextIndex) {
    }
}
