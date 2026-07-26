package com.kraft.ops;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("서비스 정보 컨트롤러 테스트")
class InfoControllerTest {

    @Test
    @DisplayName("상태 조회 시 주입된 Clock 기준 시각을 반환한다")
    void status_usesInjectedClock() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        InfoController controller = new InfoController(clock);

        InfoStatusResponse result = controller.status();

        assertThat(result.checkedAt()).isEqualTo(
                Instant.parse("2026-06-20T12:00:00Z").atZone(ZoneId.of("Asia/Seoul")).toString());
        assertThat(result.service()).isEqualTo("kraft-lotto");
        assertThat(result.timezone()).isEqualTo("Asia/Seoul");
    }
}
