/**
 * 반응형 분기점
 *
 * CSS 모듈의 `@media (min-width: 640px/1024px)`와 값을 맞춘다. CSS는 빌드 도구 없이
 * 이 상수를 공유할 수 없으므로 하드코딩을 유지한다 — 값을 바꾸면 CSS 쪽 미디어쿼리도
 * 함께 동기화해야 한다.
 */
export const BP = {
  tablet: 640,
  desktop: 1024,
  /**
   * KF-02(docs/improvement.md): 데스크톱 헤더 nav 전환 전용 브레이크포인트.
   * `desktop`(1024px)과 값이 다르다 — 1024~1100px에서 헤더 브랜드가 2줄로
   * 래핑되는 실측 결함이 있어(`e2e/responsive/header-no-wrap.spec.ts`), 헤더의
   * `.primaryNav`/`.tabBar`/`--tabbar-reserve` 전환만 1152px로 올렸다. 광고
   * 데스크톱 전환(`ad-unit.tsx`의 `DESKTOP_QUERY`)은 헤더 nav와 무관한 별개
   * 관심사라 `desktop`(1024px)을 그대로 쓴다 — 이 값과 혼동하지 말 것.
   */
  desktopNav: 1152,
} as const;
