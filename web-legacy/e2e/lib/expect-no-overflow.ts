import { expect, type Page } from "@playwright/test";

// 루트(html/body)의 scrollWidth/clientWidth만 비교하면 overflow-x: clip 같은 상위
// 레벨 설정이 실제로는 잘려나간 하위 요소의 콘텐츠 잘림을 가려버릴 수 있다 — 그래서
// 뷰포트 밖으로 삐져나온 개별 요소를 직접 찾는 방식을 쓴다. responsive.spec.ts(백엔드
// 없음 전제)와 content/*.spec.ts(픽스처 백엔드, 실콘텐츠 전제) 양쪽이 공유한다.
export async function expectNoOverflow(page: Page) {
  const overflowing = await page.evaluate(() =>
    [...document.querySelectorAll("body *")]
      .filter((el) => {
        const r = el.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) return false;
        return r.right > window.innerWidth + 1 || r.left < -1;
      })
      .filter((el) => !el.closest("[data-allow-overflow]"))
      .map((el) => el.className || el.tagName),
  );
  expect(overflowing).toEqual([]);
}
