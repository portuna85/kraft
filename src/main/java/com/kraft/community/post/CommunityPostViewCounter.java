package com.kraft.community.post;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 상세 조회의 조회수 증가만 별도 트랜잭션으로 떼어낸다.
 *
 * <p>왜 별도 빈인가: Spring의 트랜잭션은 프록시 기반이라 같은 클래스 안에서 자기 메서드를
 * 호출하면 {@code @Transactional(REQUIRES_NEW)}가 적용되지 않는다. {@link CommunityPostService}가
 * 자기 자신을 주입받는 방식은 순환 참조를 만들므로 협력 빈으로 분리한다.
 *
 * <p>왜 분리해야 하는가(운영 인시던트): 조회수 증가가 상세 조회와 같은 트랜잭션 안에 있으면,
 * 같은 글을 동시에 읽는 두 요청이 서로의 UPDATE와 충돌해 <b>조회 자체가 500</b>이 됐다.
 * MariaDB 11.6부터 {@code innodb_snapshot_isolation}이 기본 ON이고(운영 DB도 REPEATABLE-READ
 * + snapshot isolation), 이 모드에서 InnoDB는 트랜잭션이 자기 read view 이후에 바뀐 행을
 * UPDATE하려 하면 조용히 덮어쓰지 않고 {@code ER_CHECKREAD("Record has changed since last
 * read")}를 던진다. 기존 코드는 게시글을 먼저 SELECT해 스냅샷을 잡은 뒤 같은 트랜잭션에서
 * metrics를 UPDATE했기 때문에 정확히 이 조건에 걸렸다. Next.js 라우터 prefetch와 실제
 * 내비게이션이 같은 글을 거의 동시에 요청하므로 평범한 사용자 한 명만으로도 재현된다.
 *
 * <p>분리가 근본 해결인 이유: 이 트랜잭션은 UPDATE 한 문장만 실행한다. 앞선 SELECT가 없으니
 * 위반할 이전 스냅샷 자체가 없고, read view가 UPDATE 시점에 잡힌다. 그래도 만에 하나 충돌하면
 * 조회수 1을 잃을 뿐 조회는 성공해야 하므로, 호출부에서 예외를 삼킨다(부수 기능의 실패가
 * 본 기능을 깨뜨리지 않는다).
 */
@Component
public class CommunityPostViewCounter {

    private final CommunityPostMetricsRepository communityPostMetricsRepository;

    public CommunityPostViewCounter(CommunityPostMetricsRepository communityPostMetricsRepository) {
        this.communityPostMetricsRepository = communityPostMetricsRepository;
    }

    /**
     * 호출한 트랜잭션을 잠시 중단하고 새 트랜잭션에서 조회수만 1 올린다.
     * 실패해도 바깥 조회 트랜잭션은 롤백되지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(Long postId) {
        communityPostMetricsRepository.incrementViewCount(postId);
    }
}
