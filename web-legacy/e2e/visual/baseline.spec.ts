import { test, expect, type Page } from "@playwright/test";
import { gotoAndWaitForRealContent } from "../lib/goto-real-content";

// 시각 회귀 기준선: 이후 디자인 토큰/셸/컴포넌트를
// 옮기다 시각적으로 뭔가 깨지면 여기서 잡는다. 라우트·테마 조합마다 전체 페이지
// 스크린샷을 고정한다 — 베이스라인 갱신은 `npx playwright test --config=playwright.visual.config.ts
// --update-snapshots`.
//
// beforeGoto/afterGoto: 로그인 세션·추천 이력처럼 픽스처 백엔드(e2e/fixtures/backend.mjs)가
// 모르는 상태가 필요한 라우트(글쓰기·수정·추천 이력·운영 대시보드 조회 성공)를 위한 훅.
// beforeGoto는 이동 전 page.route() 등록에, afterGoto는 이동 후 상호작용(토큰 입력 등)에 쓴다.
type Route = {
  path: string;
  label: string;
  readySelector: string;
  contentOnlyScreenshot?: boolean;
  beforeGoto?: (page: Page) => Promise<void>;
  afterGoto?: (page: Page) => Promise<void>;
};

async function mockCommunitySession(page: Page, overrides?: Partial<{ userId: number }>) {
  await page.route("**/api/v1/community/session", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        loggedIn: true,
        userId: overrides?.userId ?? 42,
        nickname: "테스트유저",
        activeProviders: ["google"],
      }),
    })
  );
}

async function mockEmptyComments(page: Page, postId: number) {
  await page.route(`**/api/v1/community/posts/${postId}/comments*`, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ topLevel: [], totalTopLevelComments: 0, page: 0, totalPages: 0 }),
    })
  );
}

// M-1x: /recommend·/recommend/history가 SESSION_SCOPED_PREFIXES에 들어간 뒤로는 이
// 라우트들도 클라이언트에서 세션을 조회한다 — 픽스처 백엔드(e2e/fixtures/backend.mjs)는
// /api/v1/community/session을 모르므로, 목이 없으면 AccountMenu가 "세션을 확인하지
// 못했습니다" 오류 배너로 넘어간다(실제 익명 사용자가 보는 로그인 링크가 아니다).
// 백엔드가 익명 사용자에게 실제로 주는 형태(loggedIn:false + 사용 가능한 provider
// 목록)를 그대로 흉내낸다.
async function mockAnonymousCommunitySession(page: Page) {
  await page.route("**/api/v1/community/session", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ loggedIn: false, userId: null, nickname: null, activeProviders: ["google"] }),
    })
  );
}

async function mockRecommendationSets(page: Page, sets: unknown[]) {
  const body = JSON.stringify({ items: sets, page: 0, size: 50, totalElements: sets.length, totalPages: 1 });
  // 로그인 세션(mockCommunitySession)과 함께 쓰이면 계정 소유자 스코프 엔드포인트로
  // 요청이 나가므로(community-client.ts getMyRecommendationSets), 익명 경로와 함께
  // 두 경로 모두 같은 응답을 주도록 한다.
  await page.route("**/api/v1/recommendation-sets**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body })
  );
  await page.route("**/api/v1/community/me/recommendation-sets**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body })
  );
}

const SAMPLE_RECOMMENDATION_SET = {
  id: 1,
  strategy: "balanced",
  algorithmVersion: "v1",
  historyThroughRound: 1189,
  lockedNumbers: [],
  excludedNumbers: [],
  createdAt: "2026-01-03T12:00:00Z",
  items: [{ position: 1, numbers: [3, 11, 19, 28, 34, 42], score: 80, explanationCodes: [] }],
};

const ROUTES: readonly Route[] = [
  { path: "/", label: "홈", readySelector: ".result-panel .balls" },
  { path: "/frequency", label: "출현 통계", readySelector: ".freq-summary" },
  { path: "/community", label: "커뮤니티", readySelector: "main" },
  {
    path: "/recommend",
    label: "번호 추천",
    readySelector: "main",
    beforeGoto: mockAnonymousCommunitySession,
  },
  { path: "/saved", label: "저장 번호", readySelector: "main" },
  { path: "/stats", label: "패턴 통계", readySelector: "main" },
  { path: "/companion", label: "동반 출현", readySelector: "main" },
  { path: "/analysis", label: "번호 분석", readySelector: "main" },
  { path: "/status", label: "서비스 상태", readySelector: "main" },
  { path: "/info/data-source", label: "데이터 출처", readySelector: "main" },
  { path: "/info/methodology", label: "분석 방법론", readySelector: "main" },
  { path: "/info/faq", label: "자주 묻는 질문", readySelector: "main" },
  { path: "/info/privacy", label: "개인정보처리방침", readySelector: "main" },
  { path: "/info/terms", label: "이용약관", readySelector: "main" },
  { path: "/info/contact", label: "문의하기", readySelector: "main" },
  // FE-080: 안내 9종 중 이 3개만 시각 회귀 대상이 아니었다.
  // 새 베이스라인 18장(3라우트 × 2테마 × 3뷰포트)은 CI(Linux) actual.png를 채택해 생성한다 —
  // 로컬에서 만들면 폰트 렌더링 차이로 CI에서 전부 어긋난다.
  { path: "/info/responsible-play", label: "건전한 이용", readySelector: "main" },
  { path: "/info/community-guidelines", label: "커뮤니티 이용규칙", readySelector: "main" },
  { path: "/info/about", label: "운영자 소개", readySelector: "main" },
  {
    path: "/community/posts/1",
    label: "커뮤니티 상세",
    readySelector: "main",
    beforeGoto: (page) => mockEmptyComments(page, 1),
  },
  {
    path: "/community/write",
    label: "커뮤니티 글쓰기",
    readySelector: "main",
    beforeGoto: async (page) => {
      await mockCommunitySession(page);
      await mockRecommendationSets(page, []);
    },
  },
  {
    path: "/community/posts/1/edit",
    label: "커뮤니티 글수정",
    readySelector: "main",
    beforeGoto: (page) => mockCommunitySession(page, { userId: 42 }),
  },
  {
    path: "/recommend/history",
    label: "추천 이력",
    readySelector: "main",
    beforeGoto: async (page) => {
      await mockAnonymousCommunitySession(page);
      await mockRecommendationSets(page, [SAMPLE_RECOMMENDATION_SET]);
    },
  },
  {
    path: "/ops",
    label: "운영 대시보드",
    readySelector: ".ops-summary-grid",
    // 이 비동기 장문 화면에서 fullPage 캡처를 사용하면 sticky 헤더와 fixed 모바일
    // 내비게이션이 연속 캡처 사이에 이동해 Playwright 안정화 검사가 흔들린다.
    // 공통 크롬은 다른 라우트가 계속 검증하므로 /ops는 실제 운영 콘텐츠만 고정한다.
    contentOnlyScreenshot: true,
    beforeGoto: async (page) => {
      await page.route("**/ops-api/summary", (route) =>
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            service: "kraft-lotto",
            timezone: "Asia/Seoul",
            status: "정상",
            latestRound: 1230,
            latestDrawDate: "2026-01-03",
            checkedAt: "2026-01-03T12:00:00Z",
            fresh: true,
          }),
        })
      );
    },
    afterGoto: async (page) => {
      await page.getByPlaceholder("X-Ops-Token 값을 입력하세요").fill("secret-token");
      await page.getByRole("button", { name: "운영 상태 확인" }).click();
      // 조회 완료 후 메시지·요약 패널까지 모두 렌더링된 뒤에야 안정된다 —
      // readySelector(.ops-summary-grid)만 기다리면 상태 메시지 단락이
      // 뒤이어 붙으며 전체 페이지 높이가 한 번 더 바뀌어 스크린샷이 불안정해진다.
      await expect(page.getByText("운영 상태를 불러왔습니다.")).toBeVisible();
    },
  },
];

const THEMES = ["light", "dark"] as const;

for (const theme of THEMES) {
  test.describe(`${theme} 테마`, () => {
    test.beforeEach(async ({ page }) => {
      if (theme === "dark") {
        await page.addInitScript(() => {
          localStorage.setItem("kraft-theme", "dark");
        });
      }
    });

    for (const route of ROUTES) {
      test(`${route.label} (${route.path})`, async ({ page }) => {
        await route.beforeGoto?.(page);
        await gotoAndWaitForRealContent(page, route.path);
        await route.afterGoto?.(page);
        await expect(page.locator(route.readySelector).first()).toBeVisible();
        if (route.contentOnlyScreenshot) {
          // main은 flex로 뷰포트 높이를 채우고 긴 shell은 overflow된다. locator 캡처가
          // overflow 높이를 재계산하면 연속 이미지가 2px씩 흔들릴 수 있으므로 shell의
          // 실측 높이와 main padding으로 캡처 박스 자체를 먼저 고정한다.
          await page.addStyleTag({
            content:
              '.site-header, [data-testid="mobile-bottom-nav"] { visibility: hidden !important; }',
          });
          const main = page.locator("main");
          await main.evaluate((element) => {
            const shell = element.firstElementChild;
            if (!(shell instanceof HTMLElement)) {
              throw new Error("main shell 요소를 찾지 못했습니다.");
            }
            const mainElement = element as HTMLElement;
            const style = getComputedStyle(mainElement);
            const padding = Number.parseFloat(style.paddingTop) + Number.parseFloat(style.paddingBottom);
            mainElement.style.flex = "none";
            mainElement.style.height = `${Math.ceil(shell.getBoundingClientRect().height + padding)}px`;
          });
          await expect(main).toHaveScreenshot(`${route.label}-${theme}.png`);
        } else {
          await expect(page).toHaveScreenshot(`${route.label}-${theme}.png`, { fullPage: true });
        }
      });
    }
  });
}
