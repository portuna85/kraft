import { Skeleton } from "./states";
import styles from "./page-skeleton.module.css";

/**
 * PageSkeleton — 라우트별 `loading.tsx`가 공유하는 스트리밍 폴백(P-11).
 *
 * variant는 실제 콘텐츠 형태를 흉내 낸 정도로만 나뉜다 — 모든 라우트에 맞춤 스켈레톤을
 * 만드는 대신 4가지 골격을 재사용한다(레거시 `DataPageLoading`과 같은 접근).
 *
 * **`loading.tsx`는 아무 라우트에나 붙이면 안 된다.** `loading.tsx`(Suspense 경계)가
 * 있으면 Next는 그 라우트를 스트리밍으로 렌더하고, 그러면 본문이 아직 안 끝났어도
 * HTTP 상태 코드가 200으로 먼저 확정돼 나간다 — 이후 데이터 페치가 던져도 상태 코드는
 * 이미 200이라 5xx로 못 바뀐다. 핵심 데이터 실패를 잡지 않고 그대로 던져 5xx가 되게
 * 하는 라우트(§6.5, 예: 홈)에 `loading.tsx`를 달았다가 실제로 이 문제로 되돌렸다
 * (T-20 회귀, `00-core-failure.spec.ts`가 잡음). `.catch(() => …)`로 실패를 이미
 * 페이지 안에서 다 흡수해 아무것도 던지지 않는 라우트(`/status`, `/saved`)에만 쓴다.
 */
type Variant = "home" | "bars" | "list" | "generic";

// 폭은 CSS 클래스로 고정한다(인라인 style 금지 — CSP style-src 제약, states.tsx의
// Skeleton과 같은 이유).
const BAR_WIDTH_CLASSES = [
  styles.bar1,
  styles.bar2,
  styles.bar3,
  styles.bar4,
  styles.bar5,
  styles.bar6,
  styles.bar7,
  styles.bar8,
];

/**
 * KF-08(docs/improvement.md): `PageSkeleton`의 `list` variant 행 부분만 뽑았다 —
 * `PageSkeleton` 자신은 eyebrow/title/description 헤더 블록을 항상 함께 그려서,
 * 이미 진짜 헤더가 부모 페이지에 렌더된 자리(클라이언트 컴포넌트의 `items === null`
 * 로딩 구간)에 그대로 못 꽂는다. `PageSkeleton`의 `list` variant는 `rows` 기본값
 * (8)으로 이 컴포넌트를 그대로 호출하므로 그 렌더 결과·CSS는 이전과 동일하다
 * (순수 추출). `rows`를 받는 이유는 소비자마다 실제로 보여줄 행 수가 다르기
 * 때문이다 — 예를 들어 기본 3개만 보여주는 목록에 8행 스켈레톤을 쓰면, 로딩이
 * 끝났을 때 오히려 스켈레톤보다 콘텐츠가 작아져 시프트가 더 커질 수 있다.
 */
export function ListRowsSkeleton({ rows = 8 }: { rows?: number }) {
  return (
    // axe(aria-prohibited-attr): 일반 <div>(암묵적 generic 역할)는 aria-label을
    // 지원하지 않는다 — 명명을 지원하는 role="status"를 줘야 aria-busy/aria-label이
    // 유효해진다.
    <div className={styles.rows} role="status" aria-busy="true" aria-label="콘텐츠를 불러오는 중">
      {Array.from({ length: rows }, (_, index) => (
        <div key={index} className={styles.row}>
          <Skeleton shape="text" />
          <Skeleton shape="block" />
        </div>
      ))}
    </div>
  );
}

export function PageSkeleton({ variant = "generic" }: { variant?: Variant }) {
  return (
    <div className={styles.page} role="status" aria-busy="true" aria-label="콘텐츠를 불러오는 중">
      <div className={styles.header}>
        <div className={styles.eyebrow}>
          <Skeleton shape="text" />
        </div>
        <div className={styles.title}>
          <Skeleton shape="text" />
        </div>
        <div className={styles.description}>
          <Skeleton shape="text" />
        </div>
      </div>

      {variant === "home" && (
        <>
          <div className={styles.balls}>
            {Array.from({ length: 7 }, (_, index) => (
              <Skeleton key={index} shape="circle" />
            ))}
          </div>
          <Skeleton shape="block" />
          <div className={styles.cards}>
            {Array.from({ length: 3 }, (_, index) => (
              <div key={index} className={styles.card}>
                <Skeleton shape="text" />
                <Skeleton shape="text" />
                <Skeleton shape="text" />
              </div>
            ))}
          </div>
        </>
      )}

      {variant === "bars" && (
        <div className={styles.rows}>
          {BAR_WIDTH_CLASSES.map((widthClass, index) => (
            <div key={index} className={`${styles.bar} ${widthClass}`}>
              <Skeleton shape="block" />
            </div>
          ))}
        </div>
      )}

      {variant === "list" && <ListRowsSkeleton />}

      {variant === "generic" && (
        <div className={styles.rows}>
          {Array.from({ length: 6 }, (_, index) => (
            <div key={index} className={styles.line}>
              <Skeleton shape="text" />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
