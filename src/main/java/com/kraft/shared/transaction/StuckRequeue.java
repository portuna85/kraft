package com.kraft.shared.transaction;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 처리 도중 프로세스가 죽어 PROCESSING·SENDING인 채로 남은 작업을 대기열로 되돌리는 배치 반복(BE-36).
 * 메일 아웃박스와 세션 폐기 태스크가 같은 모양의 루프를 따로 갖고 있던 것을 한 곳에 둔다.
 * <p>
 * 적체 전체를 한 트랜잭션에 다 로딩하면 그만큼 heap과 잠금 시간이 늘어나므로 한 번에 {@value #BATCH_SIZE}건씩
 * 조회하고(B10·B11), 한 호출에서는 최대 {@value #MAX_BATCHES}번만 돈다 — 남은 적체는 다음 주기가 이어받는다.
 * 호출한 쪽이 이미 연 트랜잭션 안에서 실행한다.
 */
public final class StuckRequeue {

    /** 한 번에 조회·처리하는 최대 개수. 테스트가 배치 경계를 직접 확인할 수 있게 공개한다. */
    public static final int BATCH_SIZE = 200;

    private static final int MAX_BATCHES = 25;

    private StuckRequeue() {
    }

    /**
     * @param fetch   한 페이지(최대 {@link #BATCH_SIZE}건)를 읽는다
     * @param requeue 읽은 행 하나를 대기열로 되돌린다(상태 변경은 영속성 컨텍스트가 반영한다)
     * @return 되돌린 행의 총 개수
     */
    public static <T> int run(Function<Pageable, List<T>> fetch, Consumer<T> requeue) {
        int total = 0;
        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<T> stuck = fetch.apply(PageRequest.of(0, BATCH_SIZE));
            if (stuck.isEmpty()) {
                break;
            }
            stuck.forEach(requeue);
            total += stuck.size();
            if (stuck.size() < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
