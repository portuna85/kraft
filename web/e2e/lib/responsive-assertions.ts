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

/**
 * RSP-12(docs/improvement.md): 한 줄이어야 할 요소가 여러 줄로 접혔는지 본다.
 *
 * `assertElementMaxHeight`는 임계 픽셀을 호출부가 알아야 하고 대상 하나만 본다.
 * 여기서는 셀렉터에 걸린 **모든** 요소의 실제 줄 수를 세므로 폰트 크기·토큰 변경에
 * 영향받지 않는다.
 *
 * 줄 수를 두 방식으로 나눠 센다 — 어느 쪽도 line-height 근사를 쓰지 않는다.
 *   - 자식 요소가 있으면 자식들의 `rect.top`을 버킷팅해 행 수를 센다. flex-wrap
 *     컨테이너(LottoBallSet의 `.set`, `.itemHeader`)가 정확히 이 경우다.
 *   - 순수 텍스트면 `Range.getClientRects()`가 line box 하나당 사각형 하나를
 *     주므로 그대로 줄 수가 된다.
 *
 * 이것이 RSP-01의 **주** 검출기다. `.set`은 `flex-wrap: wrap`이라 볼이 넘치지 않고
 * 접히므로 `assertNoHorizontalOverflow`도 `assertFitsWithoutShrink`도 통과시킨다.
 */
export async function assertNoWrap(page: Page, selector: string, maxLines = 1) {
  const wrapped = await page.$$eval(
    selector,
    (elements, limit) =>
      elements
        .filter((el) => {
          const style = window.getComputedStyle(el);
          return style.display !== "none" && style.visibility !== "hidden";
        })
        .map((el) => {
          const children = Array.from(el.children).filter((child) => {
            const rect = child.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0) return false;
            // 흐름에서 빠진 자식은 행을 만들지 않는다. `.sr-only`가 정확히 이
            // 경우다 — position:absolute로 1px 상자가 되어 top이 형제와 어긋나므로
            // 걸러내지 않으면 없는 줄이 하나 더 세어진다.
            const childStyle = window.getComputedStyle(child);
            return childStyle.position !== "absolute" && childStyle.position !== "fixed";
          });

          let lines: number;
          if (children.length > 0) {
            // 같은 행의 자식은 top이 미세하게 다를 수 있다(align-items, 폰트 메트릭).
            // 2px 버킷으로 뭉쳐 "시각적으로 같은 줄"을 하나로 센다.
            const tops = new Set(
              children.map((child) => Math.round(child.getBoundingClientRect().top / 2)),
            );
            lines = tops.size;
          } else {
            const range = document.createRange();
            range.selectNodeContents(el);
            lines = range.getClientRects().length;
          }
          return { el, lines };
        })
        .filter(({ lines }) => lines > limit)
        .map(({ el, lines }) => {
          const cls = el.className ? `.${String(el.className).split(" ").join(".")}` : "";
          return `${el.tagName.toLowerCase()}${cls} (${lines}줄)`;
        }),
    maxLines,
  );
  expect(
    wrapped,
    `${maxLines}줄을 넘겨 래핑된 요소: ${wrapped.join(", ")} — 뷰포트는 넓어졌는데 안쪽 컨테이너가 좁아진 경우를 의심한다(RSP-01).`,
  ).toEqual([]);
}

/**
 * RSP-12(docs/improvement.md): 요소의 콘텐츠가 자기 폭 안에 들어가지 못하고
 * 밖으로 밀렸는지 본다.
 *
 * flex/grid 아이템은 `min-width: auto` 아래로는 줄지 않으므로, 콘텐츠가 압착되어도
 * **document** 오버플로는 생기지 않는다 — `assertNoHorizontalOverflow`가 구조적으로
 * 못 보는 종류의 결함이 여기서 잡힌다.
 *
 * 한계: 대상이 스스로 래핑할 수 있으면(`flex-wrap: wrap`, 일반 텍스트) 넘치는 대신
 * 접히므로 이 단언은 통과한다. 그 경우는 `assertNoWrap` 쪽이 담당한다. 즉 이 단언은
 * `.itemHeader`처럼 nowrap 텍스트와 버튼이 서로를 min-content까지 미는 경우용
 * **보조** 수단이다.
 */
export async function assertFitsWithoutShrink(page: Page, selector: string, tolerance = 1) {
  const squeezed = await page.$$eval(
    selector,
    (elements, tol) =>
      elements
        .filter((el) => {
          const style = window.getComputedStyle(el);
          if (style.display === "none" || style.visibility === "hidden") return false;
          // 의도적 내부 scroller는 넘치는 것이 목적이다(.tableWrap 등).
          if (style.overflowX === "auto" || style.overflowX === "scroll") return false;
          return el.scrollWidth > el.clientWidth + tol;
        })
        .map((el) => {
          const cls = el.className ? `.${String(el.className).split(" ").join(".")}` : "";
          return `${el.tagName.toLowerCase()}${cls} (scroll=${el.scrollWidth} client=${el.clientWidth})`;
        }),
    tolerance,
  );
  expect(squeezed, `콘텐츠가 컨테이너보다 넓게 눌린 요소: ${squeezed.join(", ")}`).toEqual([]);
}
