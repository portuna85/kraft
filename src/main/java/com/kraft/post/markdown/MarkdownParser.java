package com.kraft.post.markdown;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code src/vue/shared/markdown.js}의 {@code parseMarkdown}을 자바로 옮긴 서버 측 파서. Vue가 마운트하기 전
 * (또는 실패했을 때) 서버가 본문을 먼저 그리는 데 쓴다. 결과는 {@link Map}·{@link List}·{@link String}만으로
 * 이루어진 JSON 모양의 AST라, Thymeleaf 프래그먼트(layout/markdown.html)가 맵 접근만으로 그리고 공용 픽스처
 * ({@code src/test/resources/markdown/cases.json})로 자바·JS 테스트가 같은 결과를 비교할 수 있다.
 * <p>
 * 두 구현이 갈라지면 SSR과 마운트 뒤 모양이 달라지므로 로직을 바꿀 때는 두 파일을 같이 고치고 양쪽 테스트
 * (MarkdownParserTest, markdown.test.js)를 확인한다. 지원 문법은 markdown.js 상단 주석과 같다(문단·목록·굵게·
 * 기울임·코드·http/https 링크). HTML은 해석하지 않고 글자 그대로 둔다 — 렌더 쪽이 {@code th:text}만 써서 안전하다.
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

    /** 같은 문자열에서 앞으로만 움직이는 {@code indexOf} 반복(닫히지 않은 기호가 많으면 O(n²))을 직전 결과 재사용으로 줄인다. 결과는 {@code indexOf}와 같다. */
    private static final class NextOccurrence {
        private final String needle;
        private int cachedFrom = -1;
        private int cachedResult;

        NextOccurrence(String needle) {
            this.needle = needle;
        }

        int find(String text, int from) {
            if (cachedFrom >= 0 && from >= cachedFrom && (cachedResult == -1 || from <= cachedResult)) {
                return cachedResult;
            }
            cachedFrom = from;
            cachedResult = text.indexOf(needle, from);
            return cachedResult;
        }
    }

    /** {@code parseInline} 한 번 안에서 쓰는 검색 캐시 모음. */
    private static final class InlineScan {
        final NextOccurrence backtick = new NextOccurrence("`");
        final NextOccurrence strong = new NextOccurrence("**");
        final NextOccurrence em = new NextOccurrence("*");
        final NextOccurrence closeBracket = new NextOccurrence("]");
        final NextOccurrence closeParen = new NextOccurrence(")");
    }

    private static List<Object> parseInline(String text) {
        InlineScan scan = new InlineScan();
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
                int end = scan.backtick.find(text, i + 1);
                if (end != -1 && end > i + 1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("code", "text", text.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }

            if (text.startsWith("**", i)) {
                int end = findDelimiterEnd(text, i, "**", false, scan.strong);
                if (end != -1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("strong", "children", parseInline(text.substring(i + 2, end))));
                    i = end + 2;
                    continue;
                }
            }

            if (ch == '*') {
                // 단일 *만 숫자 경계를 추가로 막는다("5*3*2" 같은 곱셈 표기 오인 방지).
                int end = findDelimiterEnd(text, i, "*", true, scan.em);
                if (end != -1) {
                    flushBuffer(nodes, buffer);
                    nodes.add(node("em", "children", parseInline(text.substring(i + 1, end))));
                    i = end + 1;
                    continue;
                }
            }

            if (ch == '[') {
                LinkMatch link = tryParseLink(text, i, scan);
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
    private static int findDelimiterEnd(String text, int start, String delimiter, boolean restrictDigitBoundary,
                                        NextOccurrence closer) {
        if (restrictDigitBoundary && isDigit(charAtOrNull(text, start - 1))) {
            return -1;
        }
        int end = closer.find(text, start + delimiter.length());
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
    private static LinkMatch tryParseLink(String text, int start, InlineScan scan) {
        int closeBracket = scan.closeBracket.find(text, start + 1);
        if (closeBracket == -1 || closeBracket + 1 >= text.length() || text.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int closeParen = scan.closeParen.find(text, closeBracket + 2);
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
