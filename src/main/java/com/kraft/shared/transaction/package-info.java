/**
 * 트랜잭션 전파 규칙.
 * <p>
 * 이 프로젝트에는 한 서비스 안에서도 전파 방식이 섞여 있다 — {@code PostService}(클래스
 * {@code readOnly=true} 기본 + 메서드별 {@code SUPPORTS}·{@code NOT_SUPPORTED}·
 * {@code REQUIRED}), {@code PostLikeWriter}(전부 {@code REQUIRES_NEW}),
 * {@code PostImageCleanupBatchRunner}, {@code OutboxMailStore}, {@code SessionRevocationStore},
 * {@code EmailVerificationService}의 {@code @Lazy self} 등. 각 선택은 그 자리에 주석으로
 * 이유가 남아 있지만, 새 메서드를 추가하는 사람이 그 전체 그림을 처음부터 추론하기는
 * 어렵다. 아래는 새 코드를 쓸 때 따라야 할 기본 규칙이다 — 이미 있는 예외를 바꾸라는
 * 뜻이 아니라, 그 예외들이 왜 규칙에서 벗어났는지 판단하는 기준이다.
 *
 * <h2>1. 클래스 기본값은 {@code readOnly = true}</h2>
 * 쓰기 서비스 클래스에도 클래스 레벨 {@code @Transactional(readOnly = true)}를 기본으로
 * 두고, 실제로 쓰는 메서드에만 메서드 레벨 {@code @Transactional}(쓰기)을 얹어 덮어쓴다.
 * 클래스 기본값을 빼먹고 메서드에만 의존하면, 나중에 추가된 메서드가 조용히 쓰기
 * 트랜잭션으로 실행되는 함정이 생긴다(과거 기록: {@code @EnableCaching} +
 * {@code @DataJpaTest} 조합에서 클래스 기본값 상속을 깜빡해 캐시가 트랜잭션 밖 상태를
 * 읽은 사례).
 *
 * <h2>2. 외부 I/O는 트랜잭션 밖</h2>
 * 파일 삭제, SMTP 발송, 세션 폐기처럼 DB 트랜잭션에 참여하지 않는 부수효과는 커밋 이후로
 * 미룬다({@link com.kraft.shared.transaction.AfterCommit} 참고). 트랜잭션 안에서 먼저
 * 실행하면 이후 커밋이 실패해도 되돌릴 수 없어, DB와 외부 상태가 어긋난다(게시글은
 * 남았는데 이미지 파일만 사라지는 식).
 *
 * <h2>3. 독립적으로 커밋해야 하는 것은 별도 빈 + {@code REQUIRES_NEW}</h2>
 * 바깥 트랜잭션의 롤백과 무관하게 반드시 남아야 하는 기록(예: 실패 로그, 카운터)은
 * {@code REQUIRES_NEW}로 별도 트랜잭션을 새로 연다. 단, {@code REQUIRES_NEW} 메서드는
 * <b>반드시 다른 빈에 둔다</b> — Spring의 프록시 기반 AOP는 같은 클래스 안에서의 자기
 * 호출(self-invocation)에는 적용되지 않으므로, 같은 클래스 메서드가 이를 직접 호출하면
 * 전파 속성이 조용히 무시되고 바깥 트랜잭션 그대로 실행된다.
 *
 * <h2>4. self-invocation이 꼭 필요하면 {@code @Lazy} 자기 주입으로 명시한다</h2>
 * 규칙 3의 문제를 피하기 어려운 경우(예: {@code EmailVerificationService}가 자신의
 * 다른 트랜잭션 경계로 롤백을 격리해야 할 때)에는 필드에 자기 자신을 {@code @Lazy}로
 * 주입해 프록시를 거쳐 호출한다({@code @Lazy @Autowired private ThisService self;}).
 * 이러면 왜 굳이 이렇게 썼는지가 코드에 드러나고, 프록시를 우회하는 실수를 막는다.
 *
 * <h2>5. 잠금이 필요한 조회는 쓰기 메서드 안에서, 쓰기 메서드 자체에 전파 방식을 명시한다</h2>
 * {@code findByIdForUpdate} 같은 비관적 잠금 조회는 그 잠금을 실제로 쓰는 쓰기 트랜잭션
 * 안에서만 호출한다. 잠금과 갱신이 서로 다른 트랜잭션에 걸치면 잠금 자체가 무의미해진다.
 */
package com.kraft.shared.transaction;
