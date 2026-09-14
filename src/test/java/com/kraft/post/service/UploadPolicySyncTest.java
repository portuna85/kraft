package com.kraft.post.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 업로드 제약이 서버와 화면에서 어긋나지 않았는지 확인한다.
 * <p>
 * 같은 값이 두 언어에 존재하는 것은 없앨 수 없다 — 브라우저는 Java 상수를 읽을 수 없고,
 * 화면에서 미리 걸러 주면 쓸모없는 왕복이 줄기 때문이다. 대신 <b>한쪽만 고치면 즉시 깨지게</b>
 * 만들어 둔다. 예전에는 이 값들이 postEdit·postForm·PostImageService 세 곳에 흩어져 있었고,
 * 어긋나도 아무도 몰랐다.
 * <p>
 * 주의: 화면의 검사는 편의일 뿐 경계가 아니다. 서버는 확장자 외에 매직 바이트·픽셀 수·계정별
 * 저장량까지 본다. 그래서 여기서는 <b>화면이 아는 값이 서버와 같은지</b>만 대조하고,
 * 서버가 추가로 하는 검사는 대상에서 제외한다.
 */
class UploadPolicySyncTest {

    private static final Path JS_CONSTANTS =
            Path.of("src/main/resources/static/js/app/core/constants.js");
    private static final Path JAVA_SERVICE =
            Path.of("src/main/java/com/kraft/post/service/PostImageService.java");

    @Test
    @DisplayName("최대 파일 크기가 화면과 서버에서 같다")
    void maxFileSizeMatches() throws IOException {
        long js = Long.parseLong(captureOf(JS_CONSTANTS, "MAX_BYTES:\\s*(\\d+)\\s*\\*\\s*1024\\s*\\*\\s*1024"));
        long java = Long.parseLong(captureOf(JAVA_SERVICE, "MAX_FILE_SIZE\\s*=\\s*(\\d+)\\s*\\*\\s*1024\\s*\\*\\s*1024"));

        assertThat(js).as("constants.js와 PostImageService의 최대 파일 크기(MB)").isEqualTo(java);
    }

    @Test
    @DisplayName("허용 확장자 목록이 화면과 서버에서 같다")
    void allowedExtensionsMatch() throws IOException {
        List<String> js = quotedWordsIn(captureOf(JS_CONSTANTS, "ALLOWED_EXTENSIONS:\\s*\\[([^\\]]*)\\]"));
        List<String> java = quotedWordsIn(captureOf(JAVA_SERVICE, "ALLOWED_EXTENSIONS\\s*=\\s*Set\\.of\\(([^)]*)\\)"));

        assertThat(js).containsExactlyInAnyOrderElementsOf(java);
    }

    /**
     * 확장자만 보는 HEIC 목록은 양쪽이 같아야 한다. 서버가 <b>파일 내용</b>으로 판별하는 더 넓은
     * 집합({@code HEIF_BRANDS}: heix·mif1 등)은 화면이 흉내 낼 수 없으므로 대조하지 않는다 —
     * 화면이 더 좁은 것은 의도된 차이다.
     */
    @Test
    @DisplayName("HEIC 확장자 목록이 화면과 서버에서 같다")
    void heifExtensionsMatch() throws IOException {
        List<String> js = quotedWordsIn(captureOf(JS_CONSTANTS, "HEIF_EXTENSIONS:\\s*\\[([^\\]]*)\\]"));
        List<String> java = quotedWordsIn(captureOf(JAVA_SERVICE, "HEIF_EXTENSIONS\\s*=\\s*Set\\.of\\(([^)]*)\\)"));

        assertThat(js).containsExactlyInAnyOrderElementsOf(java);
    }

    private static String captureOf(Path file, String regex) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(regex).matcher(source);
        assertThat(matcher.find())
                .as("%s 에서 %s 를 찾지 못했습니다. 상수 선언 형태가 바뀌었다면 이 테스트도 함께 고쳐야 합니다.",
                        file, regex)
                .isTrue();
        return matcher.group(1);
    }

    /** {@code 'jpg', "png"} 같은 목록에서 따옴표 안의 값만 뽑는다. */
    private static List<String> quotedWordsIn(String list) {
        return Arrays.stream(list.split(","))
                .map(token -> token.replaceAll("[\"'\\s]", ""))
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
