package com.kraft.winningnumber;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("당첨 번호 조회 서비스 단위 테스트")
class WinningNumberQueryServiceTest {

    @Mock
    private WinningNumberRepository winningNumberRepository;

    @Mock
    private LottoDrawScheduleCalculator drawScheduleCalculator;

    private WinningNumberQueryService service;

    @BeforeEach
    void setUp() {
        service = new WinningNumberQueryService(winningNumberRepository, drawScheduleCalculator,
                Clock.system(ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("TD-003: 음수 page와 0 이하 size는 클램프돼 PageRequest.of가 IllegalArgumentException을 던지지 않는다")
    void list_clampsOutOfRangePageAndSize() {
        given(winningNumberRepository.findAllByOrderByRoundDesc(any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        service.list(-5, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(winningNumberRepository).findAllByOrderByRoundDesc(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("TD-003: 100을 초과하는 size는 100으로 클램프된다")
    void list_clampsOversizedPageSize() {
        given(winningNumberRepository.findAllByOrderByRoundDesc(any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        service.list(0, 10_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(winningNumberRepository).findAllByOrderByRoundDesc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("정상 범위의 page/size는 그대로 전달된다")
    void list_passesThroughValidPageAndSize() {
        given(winningNumberRepository.findAllByOrderByRoundDesc(any()))
                .willReturn(new PageImpl<>(java.util.List.of()));

        service.list(2, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(winningNumberRepository).findAllByOrderByRoundDesc(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }
}
