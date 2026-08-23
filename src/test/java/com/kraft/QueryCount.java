package com.kraft;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * QueryCount — 동작 중 실행된 JDBC prepared statement 수를 측정한다.
 *
 * KO-B01/BE-PERF-01(docs/improvement.md): "쿼리 수가 줄었다"를 눈으로 로그를 세지 않고
 * 테스트로 증명하기 위한 공용 하네스. Hibernate {@link Statistics}는 기본적으로 비활성화돼
 * 있으므로(런타임 오버헤드) 측정 구간에서만 켰다 끈다.
 *
 * 이 저장소는 이미 {@code RecommendationAccountDataDeletionHandlerTest}에서 같은 패턴을
 * 인라인으로 썼다 — 이 클래스는 그 코드를 재사용 가능한 형태로 추출한 것이다. 새 접근을
 * 발명하지 않는다.
 *
 * <p><b>주의</b>: {@code statisticsEnabled}는 {@link SessionFactory} 전역 상태다. 병렬 테스트
 * 실행(worker &gt; 1)에서 같은 JVM 안의 다른 테스트가 동시에 통계를 읽으면 카운트가 섞일 수
 * 있다. 필요하면 이 클래스를 쓰는 테스트를 {@code @Isolated} 또는 직렬 태스크로 묶는다.
 */
public final class QueryCount {

    private QueryCount() {
    }

    /**
     * {@code action}을 실행하는 동안 발생한 prepared statement 수를 반환한다.
     * 측정 시작 전 누적 통계를 초기화하므로, 반환값은 {@code action} 실행분만을 반영한다.
     */
    public static long measure(EntityManagerFactory entityManagerFactory, Runnable action) {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            action.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}
