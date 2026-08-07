// globals.css의 @media (min-width: 640px/1024px)와 값을 맞춘다. CSS는 빌드 도구 없이
// 이 상수를 공유할 수 없으므로 하드코딩을 유지한다 — BP 값을 바꾸면 globals.css의
// @media (min-width: 640px)/(min-width: 1024px)도 함께 동기화해야 한다.
export const BP = {
  tablet: 640,
  desktop: 1024,
} as const;
