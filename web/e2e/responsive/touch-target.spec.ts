import { test } from "@playwright/test";

import { assertMinHitArea } from "../lib/responsive-assertions";

/**
 * 상호작용 대상의 실질 hit area는 최소 44×44px이어야 한다(`--target-min`).
 * 번호 공처럼 시각 크기는 작지만 히트 영역을 `::before`로 넓힌 요소는 이미
 * 대상에서 제외한다 — 여기서는 명시적 버튼/링크만 검사한다.
 *
 * 셀렉터는 `header a`/`header [aria-label]`/`nav[aria-label="바로가기"] a`/
 * `footer a`로 한정한다. `.sr-only` 1×1 입력은 라벨이 실제 타깃이라 별도 예외가
 * 필요 없다 — 애초에 여기 셀렉터가 잡지 않는다.
 *
 * docs/improvement.md 1단계: 이 검사가 `/`에만 적용돼 다른 라우트의 회귀를
 * 못 잡았다 — 셸(header/nav/footer)이 같은 컴포넌트라도 라우트별 콘텐츠가
 * 고정 UI를 밀어내거나 계정 상태에 따라 헤더 폭이 달라질 수 있어 전 라우트로
 * 확대한다. `/ops`는 셸이 없는 라우트라(`(ops)/layout.tsx`) 이 셀렉터들이
 * 아무 요소도 찾지 못해 항상 통과한다 — 의도된 동작이다.
 *
 * RSP-11(docs/improvement.md): 폭도 390px 하나뿐이었다. hit area는 가장 좁은
 * 폭에서 먼저 깨지고, 360px는 가장 흔한 안드로이드 폭이라 document-overflow의
 * `BOUNDARY_WIDTHS` 스윕에 이미 있는 320px와 함께 셋을 돈다. `form-controls.spec.ts`는
 * `--font-size-input: max(16px, …)`가 폭과 무관하게 16px를 보장해 이미 안전하므로
 * 확장하지 않는다.
 */
const ROUTES = [
  "/",
  "/recommend",
  "/analysis",
  "/community",
  "/community/write",
  "/community/posts/1",
  "/community/posts/1/edit",
  "/status",
  "/saved",
  "/ops",
  "/info/faq",
];

const VIEWPORTS = [
  { width: 320, height: 568 },
  { width: 360, height: 800 },
  { width: 390, height: 844 },
];

for (const viewport of VIEWPORTS) {
  test.describe(`${viewport.width}px`, () => {
    test.use({ viewport });

    test.describe("44px 최소 hit area", () => {
      for (const path of ROUTES) {
        test(`${path}의 header/nav/footer 링크와 버튼이 44px 이상이다`, async ({ page }) => {
          await page.goto(path, { waitUntil: "networkidle" });
          await assertMinHitArea(page, 'nav[aria-label="바로가기"] a');
          await assertMinHitArea(page, "footer a");
          // 헤더의 실제 상호작용 컨트롤(테마 토글 등).
          await assertMinHitArea(page, "header [aria-label]");
          // KF-14(docs/improvement.md): 브랜드 링크(`.brand`)는 셸의 블록 수준
          // 내비게이션 요소라 WCAG 2.5.8의 인라인 텍스트 예외가 적용되지 않는다
          // (§2.2② 판정) — 프로젝트 자체 --target-min 계약을 따라야 한다.
          await assertMinHitArea(page, "header a");
        });
      }
    });
  });
}
