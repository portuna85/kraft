import { test } from "@playwright/test";

import { assertElementMaxHeight, assertNoHorizontalOverflow } from "../lib/responsive-assertions";

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

/**
 * RSP-25(docs/improvement.md): 위 두 describe는 폭 경계를 자세히 보지만 **짧은
 * fixture 닉네임(`테스터`, 3자)만 쓴다.** 최대 길이 콘텐츠가 테스트 축에 없다.
 *
 * 백엔드는 OAuth 닉네임을 최대 100자까지 허용하고
 * (`CommunityOAuthAttributes.java`), `account-menu.tsx:69,80`은 그것을 버튼에
 * 그대로 출력한다. `button.module.css`에도 `.headerActions`
 * (`shell.module.css:47-51`)에도 `max-width`·`min-width: 0`·말줄임이 없으므로
 * 계정 라벨이 무한정 늘어나 브랜드와 테마 토글을 밀어낸다.
 *
 * 폭 축도 함께 넓힌다 — 기존 배열은 1024px부터라 모바일 헤더를 한 번도 재지
 * 않았다. 320/390px에서는 데스크톱 메뉴가 없는 대신 헤더 폭 자체가 좁다.
 */
const LONG_NICKNAME_WIDTHS = [320, 390, 1024, 1151, 1152, 1280];

/** 한글 100자 — `word-break: keep-all`이 걸린 어절 단위 줄바꿈 경로. */
const LONG_KO_NICKNAME = "가나다라마".repeat(20);
/** 공백 없는 ASCII 100자 — `overflow-wrap: anywhere`가 걸린 강제 분리 경로. */
const LONG_ASCII_NICKNAME = "a".repeat(100);

/**
 * 지금 실제로 깨지는 폭. a14938e가 세운 관행대로 "예상된 실패"로 표시해 CI를
 * 영구히 빨갛게 만들지 않되, 통과하는 폭은 조건에서 빼 회귀 감지력을 남긴다.
 * PR 2에서 계정 라벨에 max-width·말줄임·min-width:0을 넣으면 이 목록의 테스트가
 * "예상과 다르게 통과함"으로 실패하므로, 목록을 지우는 것이 그 수정의 일부다.
 *
 * 실측(2026-08-21, chromium):
 *   한글 100자  — 320px 헤더 319px / 390px 192px / 1152·1280px 152px (계약 64px)
 *   ASCII 100자 — 320·390px에서 document scrollWidth 1115px (뷰포트 320/390px),
 *                 원인은 `.headerActions`
 */
const KNOWN_BROKEN_WIDTHS = {
  "한글 100자": [320, 390, 1152, 1280],
  "공백 없는 ASCII 100자": [320, 390, 1024, 1152, 1280],
} satisfies Record<string, number[]>;

for (const [label, nickname] of [
  ["한글 100자", LONG_KO_NICKNAME],
  ["공백 없는 ASCII 100자", LONG_ASCII_NICKNAME],
] as const) {
  test.describe(`RSP-25: ${label} 닉네임이 헤더를 밀어내지 않는다`, () => {
    // 전역 `__test__/session`이 아니라 컨텍스트 쿠키로 로그인 상태를 만든다 —
    // 이 트랙은 fullyParallel이라 전역을 바꾸면 다른 스펙이 이 닉네임으로 렌더된
    // 셸을 재게 된다(실제로 document-overflow.spec.ts의 320×512가 그렇게 빨개졌다).
    test.beforeEach(async ({ context }) => {
      await context.addCookies([
        {
          name: "e2e-nickname",
          value: encodeURIComponent(nickname),
          url: "http://127.0.0.1:3115",
        },
      ]);
    });

    for (const width of LONG_NICKNAME_WIDTHS) {
      test(`${width}px에서 헤더가 넘치거나 높이가 커지지 않는다`, async ({ page }) => {
        test.fail(
          KNOWN_BROKEN_WIDTHS[label].includes(width),
          "RSP-25: 계정 라벨에 max-width·말줄임·min-width:0이 없다",
        );
        await page.setViewportSize({ width, height: 900 });
        await page.goto("/recommend", { waitUntil: "networkidle" });

        // 헤더 높이는 --layout-header-h(64px) 계약이다 — 계정 라벨이 래핑되면
        // 헤더가 통째로 늘어나 sticky 오프셋(scroll-padding-top)까지 어긋난다.
        await assertElementMaxHeight(page, "header", 64);
        await assertNoHorizontalOverflow(page);
      });
    }
  });
}
