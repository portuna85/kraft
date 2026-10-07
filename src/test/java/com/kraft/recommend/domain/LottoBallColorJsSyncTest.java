package com.kraft.recommend.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공 색 구간이 서버({@link LottoBallColor})와 화면({@code src/vue/recommend/ballColor.js})에서 어긋나지 않았는지
 * 확인한다. 서버는 첫 화면의 최신 회차 공을, 화면은 생성된 조합을 그리므로 같은 규칙이 두 언어에 있어야 한다
 * ({@code UploadPolicySyncTest}와 같은 방식 — 한쪽만 고치면 즉시 깨진다). 예전에는 두 구현을 대조하는 것이
 * 없어서 어긋나도 아무도 몰랐다.
 */
class LottoBallColorJsSyncTest {

    private static final Path JS_BALL_COLOR = Path.of("src/vue/recommend/ballColor.js");
    private static final Pattern BAND = Pattern.compile("upTo:\\s*(\\d+),\\s*name:\\s*'(\\w+)'");
    private static final Pattern LAST = Pattern.compile("BALL_COLOR_LAST\\s*=\\s*'(\\w+)'");

    @Test
    @DisplayName("1~45 모든 번호의 색 이름이 화면과 서버에서 같다")
    void everyNumberHasTheSameColor() throws IOException {
        String source = Files.readString(JS_BALL_COLOR, StandardCharsets.UTF_8);

        List<int[]> upTo = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher bands = BAND.matcher(source);
        while (bands.find()) {
            upTo.add(new int[]{Integer.parseInt(bands.group(1))});
            names.add(bands.group(2));
        }
        Matcher last = LAST.matcher(source);
        assertThat(last.find()).as("ballColor.js의 BALL_COLOR_LAST").isTrue();
        assertThat(names).as("ballColor.js의 구간 정의를 읽지 못했다").isNotEmpty();

        for (int n = 1; n <= 45; n++) {
            String js = last.group(1);
            for (int i = 0; i < upTo.size(); i++) {
                if (n <= upTo.get(i)[0]) {
                    js = names.get(i);
                    break;
                }
            }
            assertThat(js).as("번호 %d의 색", n).isEqualTo(LottoBallColor.colorName(n));
        }
    }
}
