import { expect, test } from "@playwright/test";

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
  test.beforeEach(async ({ request, context }) => {
    await request.put(
      "http://127.0.0.1:4115/__test__/session?loggedIn=true&userId=1&nickname=%ED%85%8C%EC%8A%A4%ED%84%B0",
    );
    // FE-SEC-02(docs/improvement.md): request fixture는 page의 쿠키 저장소와
    // 별개라 __test__/session만으로는 (session)/layout.tsx의 kraft_logged_in
    // 게이트를 못 지난다 — 컨텍스트 쿠키로 직접 심는다.
    await context.addCookies([
      { name: "kraft_logged_in", value: "1", url: "http://127.0.0.1:3115" },
    ]);
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

for (const [label, nickname] of [
  ["한글 100자", LONG_KO_NICKNAME],
  ["공백 없는 ASCII 100자", LONG_ASCII_NICKNAME],
] as const) {
  test.describe(`RSP-25: ${label} 닉네임이 헤더를 밀어내지 않는다`, () => {
    // 전역 `__test__/session`이 아니라 컨텍스트 쿠키로 로그인 상태를 만든다 —
    // 이 트랙은 fullyParallel이라 전역을 바꾸면 다른 스펙이 이 닉네임으로 렌더된
    // 셸을 재게 된다(실제로 document-overflow.spec.ts의 320×512가 그렇게 빨개졌다).
    // FE-SEC-02(docs/improvement.md): kraft_logged_in도 함께 심어야
    // (session)/layout.tsx가 신원 조회(fetchSession)를 건너뛰지 않는다 —
    // 안 심으면 e2e-nickname을 읽는 sessionFromCookie 자체가 호출되지 않는다.
    test.beforeEach(async ({ context }) => {
      await context.addCookies([
        {
          name: "e2e-nickname",
          value: encodeURIComponent(nickname),
          url: "http://127.0.0.1:3115",
        },
        { name: "kraft_logged_in", value: "1", url: "http://127.0.0.1:3115" },
      ]);
    });

    for (const width of LONG_NICKNAME_WIDTHS) {
      test(`${width}px에서 헤더가 넘치거나 높이가 커지지 않는다`, async ({ page }) => {
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

/**
 * RSP-18(docs/improvement.md): sticky 헤더의 backdrop-filter는 스크롤하는 모든
 * 프레임에서 헤더 뒤 영역을 재샘플링·재블러한다 — 어떤 성능 게이트에도 걸리지
 * 않는 비용이다(lighthouse-budget.mjs가 throttlingMethod:"provided"라 로드
 * 시점 지표만 잰다). 실제 프레임 비용은 수동 프로파일링으로만 확인할 수 있지만,
 * "1152px 미만은 불투명, 그 이상만 블러"라는 **계약**은 computed style로 직접
 * 확인할 수 있다 — 그 계약이 조용히 되돌려지는 회귀를 여기서 잡는다.
 */
test.describe("RSP-18: 헤더 블러는 1152px부터만 켜진다", () => {
  const BLUR_BOUNDARY_WIDTHS = [1024, 1151, 1152, 1280];

  for (const width of BLUR_BOUNDARY_WIDTHS) {
    test(`${width}px에서 backdrop-filter 적용 여부가 계약과 일치한다`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto("/", { waitUntil: "networkidle" });

      const backdropFilter = await page
        .locator("header")
        .first()
        .evaluate((el) => window.getComputedStyle(el).backdropFilter);

      // Chromium computed style은 미적용 시 "none"을 돌려준다.
      if (width >= 1152) {
        expect(backdropFilter).not.toBe("none");
      } else {
        expect(backdropFilter).toBe("none");
      }
    });
  }
});
