package com.kraft.recommend.service;

import com.kraft.recommend.domain.WinningDraw;
import com.kraft.recommend.domain.WinningDrawRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 가장 최근 회차 당첨 정보를 읽는 유일한 진입점. 번호 추천·관리자 수집 화면의 컨트롤러가 각자 리포지토리를 직접 부르지 않고 여기로 모은다 — 나중에 이 값에 캐시를 걸어야 하면 여기 한 곳만 고친다.
 */
@RequiredArgsConstructor
@Service
public class LatestDrawService {

    private final WinningDrawRepository repository;

    @Transactional(readOnly = true)
    public Optional<WinningDraw> latest() {
        return repository.findTopByOrderByRoundNoDesc();
    }
}
