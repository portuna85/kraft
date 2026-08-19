import { expect, type Page } from "@playwright/test";

/**
 * 반응형 회귀를 픽셀 스크린샷이 아니라 프로그래매틱 단언으로 고정한다 — Phase 1
 * (docs/improvement_claude_fe.md §7.3, docs/improvement_codex_fe.md §8.3).
 *
 * 시각 회귀(baseline.spec.ts)는 "무엇이 달라졌는가"는 보여주지만 "왜 실패했는가"는
 * 스크린샷 diff를 눈으로 봐야 알 수 있다. 여기 단언들은 실패 시 테스트 이름과 메시지만
 * 으로 원인(overflow, 44px 미달, 16px 미달, 가려짐)을 알 수 있게 한다.
 */

/**
 * 의도적 내부 scroller(표 가로 스크롤 등)를 제외하고 문서 자체가 가로로
 * 스크롤되지 않아야 한다. `.tableWrap`류는 스스로 `overflow-x`를 갖는 것이
 * 목적이므로 document 레벨 overflow 검사에서 예외로 둔다.
 */
export async function assertNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => {
    const root = document.documentElement;
    const culprits: string[] = [];
    // scrollWidth 자체는 어떤 요소가 원인인지 말해주지 않는다 — 문서 안의 모든
    // 요소를 순회해 실제로 뷰포트 오른쪽 경계를 넘는 후보를 같이 보고한다.
    document.querySelectorAll("body *").forEach((el) => {
      const rect = el.getBoundingClientRect();
      if (rect.right > root.clientWidth + 1) {
        const cls = el.className ? `.${String(el.className).split(" ").join(".")}` : "";
        culprits.push(`${el.tagName.toLowerCase()}${cls} right=${Math.round(rect.right)}`);
      }
    });
    return {
      scrollWidth: root.scrollWidth,
      clientWidth: root.clientWidth,
      url: location.href,
      culprits: culprits.slice(0, 5),
    };
  });
  expect(
    overflow.scrollWidth,
    `${overflow.url}: documentElement.scrollWidth(${overflow.scrollWidth}) > clientWidth(${overflow.clientWidth}) — 의도치 않은 document 가로 스크롤. 후보: ${overflow.culprits.join(", ")}`,
  ).toBeLessThanOrEqual(overflow.clientWidth);
}

/**
 * 지정한 셀렉터에 해당하는 모든 상호작용 요소의 실질 hit area가 최소
 * `min`(기본 44) × `min`px 이상이어야 한다. 비상호작용 장식 요소(번호 공 등)는
 * 호출 측에서 셀렉터를 좁혀 제외한다.
 */
export async function assertMinHitArea(page: Page, selector: string, min = 44) {
  const undersized = await page.$$eval(
    selector,
    (elements, minSize) =>
      elements
        .filter((el) => {
          const rect = el.getBoundingClientRect();
          if (rect.width === 0 && rect.height === 0) return false; // 숨김 요소는 제외
          return rect.width < minSize || rect.height < minSize;
        })
        .map((el) => {
          const rect = el.getBoundingClientRect();
          return `${el.tagName.toLowerCase()}${el.className ? "." + String(el.className).split(" ").join(".") : ""} (${Math.round(rect.width)}x${Math.round(rect.height)})`;
        }),
    min,
  );
  expect(undersized, `${min}px 미만 hit area를 가진 요소: ${undersized.join(", ")}`).toEqual([]);
}

/**
 * 모든 텍스트 입력 컨트롤의 computed font-size가 16px 이상이어야 한다.
 * iOS Safari는 16px 미만 컨트롤에 포커스가 들어가면 페이지를 자동 확대한다.
 */
export async function assertFormControlFontSizeAtLeast16px(page: Page) {
  const tooSmall = await page.$$eval("input, textarea, select", (elements) =>
    elements
      .filter((el) => {
        const type = (el as HTMLInputElement).type;
        if (type === "checkbox" || type === "radio" || type === "hidden") return false;
        const style = window.getComputedStyle(el);
        if (style.display === "none" || style.visibility === "hidden") return false;
        return parseFloat(style.fontSize) < 16;
      })
      .map((el) => {
        const style = window.getComputedStyle(el);
        const name =
          el.getAttribute("name") ?? el.getAttribute("aria-label") ?? el.id ?? "(no-name)";
        return `${el.tagName.toLowerCase()}[${name}] font-size=${style.fontSize}`;
      }),
  );
  expect(tooSmall, `16px 미만 폼 컨트롤: ${tooSmall.join(", ")}`).toEqual([]);
}

/**
 * 지정한 셀렉터의 첫 요소가 계산된 높이 `max`px을 넘지 않아야 한다. 텍스트가
 * 2줄로 래핑되면 1줄 대비 높이가 거의 두 배가 되므로, 문서 자체는 넘치지 않아도
 * (`assertNoHorizontalOverflow`로는 못 잡는) 래핑을 이 방식으로 검출한다.
 */
export async function assertElementMaxHeight(page: Page, selector: string, max: number) {
  const height = await page
    .locator(selector)
    .first()
    .evaluate((el) => el.getBoundingClientRect().height);
  expect(
    height,
    `${selector}의 높이(${Math.round(height)}px)가 ${max}px를 초과한다 — 줄바꿈(래핑) 의심`,
  ).toBeLessThanOrEqual(max);
}

/**
 * 고정 UI(헤더, 하단 탭바, 토스트 등)가 페이지의 마지막 focusable 요소나 주요
 * CTA를 가리지 않아야 한다. `fixedSelectors`로 가려서는 안 되는 고정 요소를,
 * `targetSelector`로 가려지면 안 되는 대상을 지정한다.
 */
export async function assertNotOccludedByFixedUi(
  page: Page,
  targetSelector: string,
  fixedSelectors: string[],
) {
  const target = page.locator(targetSelector).last();
  if ((await target.count()) === 0) return;
  const targetBox = await target.boundingBox();
  if (!targetBox) return;

  for (const fixedSelector of fixedSelectors) {
    const fixedEl = page.locator(fixedSelector).first();
    if ((await fixedEl.count()) === 0) continue;
    const fixedBox = await fixedEl.boundingBox();
    if (!fixedBox) continue;

    const overlapX = Math.max(
      0,
      Math.min(targetBox.x + targetBox.width, fixedBox.x + fixedBox.width) -
        Math.max(targetBox.x, fixedBox.x),
    );
    const overlapY = Math.max(
      0,
      Math.min(targetBox.y + targetBox.height, fixedBox.y + fixedBox.height) -
        Math.max(targetBox.y, fixedBox.y),
    );
    expect(
      overlapX > 0 && overlapY > 0,
      `${targetSelector}가 고정 요소 ${fixedSelector}에 가려짐 (target=${JSON.stringify(targetBox)}, fixed=${JSON.stringify(fixedBox)})`,
    ).toBe(false);
  }
}
