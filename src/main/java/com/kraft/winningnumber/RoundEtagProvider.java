package com.kraft.winningnumber;

import com.kraft.common.web.EtagVersionSource;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 캐싱 가능한 엔드포인트의 ETag를 도메인 버전(회차 번호)에서 파생한다.
 * 과거 회차 상세는 보정 후에도 값이 바뀌어야 하므로 MD5 폴백(바디 해시)에 맡기고,
 * 변경 가능 경로는 최신 회차 번호 + 단조 증가 성분으로 계산한다.
 * NOTE: bump 카운터는 인스턴스별 상태다 — 수평 확장 시 인스턴스마다 부팅 시점부터
 * 독립적으로 증가하므로, 같은 회차 데이터라도 인스턴스마다 다른 ETag 문자열을 낼 수
 * 있다. 이는 캐시 조회에서 잘못된 데이터를 주는 문제가 아니라(값이 다르면 클라이언트가
 * 그냥 다시 받아갈 뿐), 로드밸런서가 요청을 인스턴스 사이로 분산할 때 If-None-Match가
 * 실제보다 자주 미스로 판정되는 정도의 캐시 효율 저하다.
 * KB-17: common.web에 있던 시절 winningnumber를 직접 참조해 common→feature 순환의
 * 한 변을 이뤘다 — 도메인을 아는 이 패키지로 옮기고, common.web은 EtagVersionSource
 * 인터페이스로만 이 클래스를 바라본다.
 */
@Component
public class RoundEtagProvider implements EtagVersionSource {

    private static final String UNKNOWN = "\"round-unknown\"";
    // 회차 번호만으로는 보정을 표현할 수 없는 경로 — 항상 MD5 폴백(응답 바디 해시)을 쓰도록 강제한다.
    private static final Set<String> MD5_FALLBACK_PATHS = Set.of(
            "/api/v1/rounds/freshness",
            "/api/v1/status/incidents"
    );

    private final AtomicReference<String> mutableETag = new AtomicReference<>(UNKNOWN);
    // 과거 회차를 재수집해도 mutableETag가 그 시절 값으로 회귀하지 않도록 단조 증가시키는 성분.
    private final AtomicLong bump = new AtomicLong();
    private final WinningNumberRepository winningNumberRepository;

    public RoundEtagProvider(WinningNumberRepository winningNumberRepository) {
        this.winningNumberRepository = winningNumberRepository;
    }

    @PostConstruct
    void init() {
        winningNumberRepository.findTopByOrderByRoundDesc()
                .ifPresent(wn -> mutableETag.set(formatEtag(wn.getRound())));
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCollected(WinningNumbersCollectedEvent event) {
        if (event.dataChanged()) {
            int latest = winningNumberRepository.findTopByOrderByRoundDesc()
                    .map(wn -> wn.getRound())
                    .orElse(event.round());
            mutableETag.set(formatEtag(latest));
        }
    }

    private String formatEtag(int latestRound) {
        return "\"round-%d-b%d\"".formatted(latestRound, bump.incrementAndGet());
    }

    @Override
    public String etagForPath(String requestPath) {
        if (MD5_FALLBACK_PATHS.contains(requestPath)) {
            return null;
        }
        String version = mutableETag.get();
        return UNKNOWN.equals(version) ? null : version;
    }
}
