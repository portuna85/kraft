import { test } from "@playwright/test";

import { assertElementMaxHeight } from "../lib/responsive-assertions";

/**
 * KF-02(docs/improvement.md) — 데스크톱 헤더가 1024~1100px에서 깨졌었다.
 *
 * 근인: 데스크톱 nav 전환이 1024px에 걸려 있어, `nav-items.ts`의 `PRIMARY_NAV`
 * 9개 항목이 `.brand`·`.headerActions`와 좁은 공간을 다퉜다(`.brand`에
 * `white-space:nowrap`도 없었다). §10 4단계에서 데스크톱 nav 전환 자체를
 * 1152px로 올렸다(`shared/config/breakpoints.ts`의 `BP.desktopNav`,
 * `shell.module.css`의 `.primaryNav`/`.tabBar`/`--tabbar-reserve`가 모두 함께
 * 전환). 1152px는 이 스펙이 실측으로 확인한, 이미 안전했던 경계다.
 *
 * KF-14(docs/improvement.md)로 `.brand`에 `min-height: var(--target-min)`
 * (44px)을 추가해, 래핑 안 된 1줄 상태의 실측 높이도 ~29px → 44px로 늘었다 —
 * 임계값을 그 높이 + 여유로 올린다. 실제로 2줄로 래핑되면 배 이상(80px+)이라
 * 이 임계값으로도 충분히 구분된다.
 */
const WIDTHS = [1024, 1060, 1100, 1151, 1152, 1280];
const MAX_SINGLE_LINE_HEIGHT = 48;

test.describe("데스크톱 헤더 브랜드가 좁은 폭에서 래핑되지 않는다", () => {
  for (const width of WIDTHS) {
    test(`${width}px에서 브랜드 링크 높이가 ${MAX_SINGLE_LINE_HEIGHT}px 이하다`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });
      await assertElementMaxHeight(page, "header a", MAX_SINGLE_LINE_HEIGHT);
    });
  }
});

/**
 * 로그인 상태에서도 확인한다 — 로그인 시 `AccountControl`이 닉네임 라벨을 가진
 * `AccountMenu`를 렌더해(로그아웃 상태의 `LoginPopover`보다 넓을 수 있음)
 * 헤더 폭 압박이 달라진다(감사 권고: "계정 라벨은 로그인 상태에서 더 길어지므로
 * 인증 상태 기준으로 브레이크포인트를 잡을 것"). `(session)` 셸에서만
 * `AccountControl`이 쓰이므로 `/recommend`로 확인한다.
 */
test.describe("로그인 상태에서도 데스크톱 헤더 브랜드가 래핑되지 않는다", () => {
  test.beforeEach(async ({ request }) => {
    await request.put(
      "http://127.0.0.1:4115/__test__/session?loggedIn=true&userId=1&nickname=%ED%85%8C%EC%8A%A4%ED%84%B0",
    );
  });

  test.afterEach(async ({ request }) => {
    await request.post("http://127.0.0.1:4115/__test__/reset");
  });

  for (const width of WIDTHS) {
    test(`${width}px 로그인 상태에서 브랜드 링크 높이가 ${MAX_SINGLE_LINE_HEIGHT}px 이하다`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/recommend", { waitUntil: "networkidle" });
      await assertElementMaxHeight(page, "header a", MAX_SINGLE_LINE_HEIGHT);
    });
  }
});
