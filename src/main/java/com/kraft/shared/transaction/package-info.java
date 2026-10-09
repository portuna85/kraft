/**
 * 트랜잭션 전파 규칙 — 새 코드가 따를 기본값이다(기존 예외는 각 자리의 주석이 이유를 설명한다).
 * <ol>
 * <li><b>클래스 기본값은 {@code readOnly = true}</b>: 쓰는 메서드에만 메서드 레벨 {@code @Transactional}을 얹는다.
 *     빼먹으면 나중에 추가한 메서드가 조용히 쓰기 트랜잭션으로 돈다.</li>
 * <li><b>외부 I/O는 트랜잭션 밖</b>: 파일 삭제·SMTP·세션 폐기는 커밋 후로 미룬다
 *     ({@link com.kraft.shared.transaction.AfterCommit}). 먼저 하면 커밋이 실패해도 되돌릴 수 없다.</li>
 * <li><b>독립 커밋은 별도 빈 + {@code REQUIRES_NEW}</b>: 같은 클래스 안 호출(self-invocation)은 프록시를 거치지
 *     않아 전파 속성이 무시된다.</li>
 * <li>self-invocation이 꼭 필요하면 {@code @Lazy @Autowired private ThisService self;}로 프록시를 거친다.</li>
 * <li><b>잠금 조회는 갱신과 같은 쓰기 트랜잭션 안에서</b>: {@code findByIdForUpdate}의 잠금은 다른 트랜잭션에
 *     걸치면 무의미하다.</li>
 * </ol>
 */
package com.kraft.shared.transaction;
