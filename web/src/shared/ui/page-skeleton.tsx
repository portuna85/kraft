import { Skeleton } from "./states";
import styles from "./page-skeleton.module.css";

/**
 * PageSkeleton — 라우트별 `loading.tsx`가 공유하는 스트리밍 폴백(improvement_fe.md P-11).
 *
 * variant는 실제 콘텐츠 형태를 흉내 낸 정도로만 나뉜다 — 모든 라우트에 맞춤 스켈레톤을
 * 만드는 대신 4가지 골격을 재사용한다(레거시 `DataPageLoading`과 같은 접근).
 *
 * 루트 `app/loading.tsx`는 반드시 `generic`을 써야 한다 — 레거시 FE-006(홈 전용
 * 스켈레톤이 다른 모든 라우트에 새는 버그)의 재발을 막기 위함이다. 라우트별
 * `loading.tsx`만 더 구체적인 variant를 고른다.
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

export function PageSkeleton({ variant = "generic" }: { variant?: Variant }) {
  return (
    <div className={styles.page} aria-busy="true" aria-label="콘텐츠를 불러오는 중">
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

      {variant === "list" && (
        <div className={styles.rows}>
          {Array.from({ length: 8 }, (_, index) => (
            <div key={index} className={styles.row}>
              <Skeleton shape="text" />
              <Skeleton shape="block" />
            </div>
          ))}
        </div>
      )}

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
