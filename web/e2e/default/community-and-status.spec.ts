import { expect, test } from "@playwright/test";

import { fixtureBackendUrl } from "../lib/fixture-backend";

/**
 * 커뮤니티 읽기 흐름 + 서비스 상태 + 안내 라우트 — T-9, T-10
 *
 * 익명 사용자는 커뮤니티를 읽을 수만 있다(작성·댓글·좋아요는 로그인 필요) — 그래서
 * 이 트랙은 읽기 경로에 집중한다. 쓰기 흐름(작성·댓글·신고)은 컴포넌트 테스트가
 * 로그인 세션을 목으로 만들어 이미 검증했다(Step A/B, `post-form.test.tsx`·
 * `comment-section.test.tsx`).
 */
test.describe("커뮤니티·상태·안내 읽기", () => {
  test("커뮤니티 목록에서 글 상세로 이동한다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByRole("heading", { name: "첫 글입니다" })).toBeVisible();
    await expect(page.getByText("본문 첫 줄")).toBeVisible();
  });

  // KF-26(FE-OPT-23, docs/improvement.md): generateMetadata·페이지 본문이 각각
  // getPost를 불러 캐시가 없으면 방문 1회에 조회수가 2 오른다. React cache()로
  // 감싼 뒤 정확히 1만 오르는지 픽스처의 조회수 카운터로 확인한다.
  test("게시글 상세 방문 1회에 조회수가 정확히 1만 오른다", async ({ page, request, baseURL }) => {
    await request.post(`${fixtureBackendUrl(baseURL)}/__test__/reset`);

    await page.goto("/community/posts/1");
    await expect(page.getByText("조회 42")).toBeVisible();

    await page.reload();
    await expect(page.getByText("조회 43")).toBeVisible();
  });

  test("게시글 상세가 상위 댓글과 답글을 함께 보여준다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("첫 댓글")).toBeVisible();
    await expect(page.getByText("답글입니다")).toBeVisible();
  });

  test("삭제된 댓글은 tombstone 문구로 자리를 지킨다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("삭제된 댓글입니다.")).toBeVisible();
  });

  test("비로그인은 댓글 작성 폼 대신 안내를 본다", async ({ page }) => {
    await page.goto("/community/posts/1");
    await expect(page.getByText("로그인하면 댓글을 남길 수 있습니다.")).toBeVisible();
  });

  test("글이 1페이지뿐이면 페이지네이션 대신 총 건수만 보인다", async ({ page }) => {
    await page.goto("/community");
    await expect(page.getByRole("heading", { name: "첫 글입니다" })).toBeVisible();
    await expect(page.getByText("총 1건")).toBeVisible();
    await expect(page.getByRole("navigation", { name: "페이지 이동" })).not.toBeVisible();
  });

  // kraft-redesign-plan.md P2 "Community discovery improvements" — 기본
  // 목록(필터·정렬·페이지 전부 기본값)에서만 인기 글 미리보기가 보이고,
  // 검색·분류로 좁힌 화면에서는 발견이 아니라 중복 안내가 되므로 감춘다.
  test("기본 목록에서는 이번 주 인기 글 미리보기가 보이고, 분류로 좁히면 사라진다", async ({
    page,
  }) => {
    await page.goto("/community");
    const trending = page.getByRole("region", { name: "이번 주 인기 글" });
    await expect(trending).toBeVisible();
    await expect(trending.getByRole("link", { name: "첫 글입니다" })).toHaveAttribute(
      "href",
      "/community/posts/1",
    );

    await page.goto("/community?category=WIN_STORY");
    await expect(page.getByRole("region", { name: "이번 주 인기 글" })).not.toBeVisible();
  });

  test("여러 페이지가 있으면 처음·이전·다음·마지막으로 이동한다 (레거시 FE-052)", async ({
    page,
  }) => {
    await page.goto("/community?category=WIN_STORY");
    const pagination = page.getByRole("navigation", { name: "페이지 이동" });
    await expect(pagination).toBeVisible();
    await expect(page.getByText("1 / 3")).toBeVisible();
    // KF-15(docs/improvement.md): 경계 항목은 이제 링크가 아니라 비링크 텍스트다.
    await expect(pagination.getByRole("link", { name: "처음" })).toHaveCount(0);
    await expect(pagination.getByText("처음", { exact: true })).toHaveAttribute(
      "aria-disabled",
      "true",
    );

    await pagination.getByRole("link", { name: "다음" }).click();
    await expect(page).toHaveURL(/page=1/);
    await expect(page.getByText("2 / 3")).toBeVisible();

    await pagination.getByRole("link", { name: "마지막" }).click();
    await expect(page).toHaveURL(/page=2/);
    await expect(page.getByText("3 / 3")).toBeVisible();
    await expect(pagination.getByRole("link", { name: "다음" })).toHaveCount(0);
    await expect(pagination.getByText("다음", { exact: true })).toHaveAttribute(
      "aria-disabled",
      "true",
    );

    // buildListHref는 page=0을 URL에 남기지 않는다(기본값 생략) — 그래서 "처음"은
    // 콘텐츠로만 확인한다.
    await pagination.getByRole("link", { name: "처음" }).click();
    await expect(page.getByText("1 / 3")).toBeVisible();
  });

  test("/status가 데이터 신선도와 수집·보정 이력을 렌더한다", async ({ page }) => {
    await page.goto("/status");
    await expect(page.getByText("정상 반영")).toBeVisible();
    await expect(page.getByText("당첨번호 자동 수집")).toBeVisible();
  });

  test("/info/faq가 렌더된다", async ({ page }) => {
    await page.goto("/info/faq");
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  });
});
