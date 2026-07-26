import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import path from "node:path";

// globals.css 캐스케이드(모바일 퍼스트, 여러 파일로 분할된 구조)의 계산값 회귀 방지망.
// 실제 앱은 이 e2e 환경에 백엔드가 없어 홈/`/frequency` 등 데이터 페이지가 항상
// error.tsx를 렌더하므로, 그 페이지들의 실제 마크업을 통해 회귀를 잡을 수 없다.
// 대신 globals.css 원본을 그대로 빈 페이지에 주입하고, 실제 컴포넌트와 동일한 부모-자식
// 중첩 구조(예: .balls의 부모가 .panel인 것 — 부모의 속성이 의도치 않게 상속되는
// 버그가 바로 이 중첩 때문에 생길 수 있다)를 가진 최소 마크업으로 계산값을 스냅숏한다.
// 순수 CSS라 빌드 도구 없이도 브라우저에 그대로 주입 가능하다.
//
// globals.css는 @import만 나열한 매니페스트라, page.addStyleTag로 빈 페이지(about:blank
// 기반 page.setContent)에 그대로 넣으면 상대 경로 @import를 해석할 기반 URL이 없어
// 실패한다 — 매니페스트를 직접 읽어 각 @import 대상을 순서대로 이어붙인다(Next 빌드가
// 하는 일을 테스트에서도 그대로 재현).
function loadGlobalsCss(): string {
  const appDir = path.join(__dirname, "../src/app");
  const manifest = readFileSync(path.join(appDir, "globals.css"), "utf8");
  const importRe = /@import\s+"([^"]+)";/g;
  let combined = "";
  let m: RegExpExecArray | null;
  while ((m = importRe.exec(manifest))) {
    combined += readFileSync(path.join(appDir, m[1]), "utf8") + "\n";
  }
  return combined;
}
const CSS = loadGlobalsCss();

// 실제 페이지에서의 부모-자식 관계를 그대로 재현한다 — 이 중첩이 어긋나면 gap/padding
// 상속 계산이 실제와 달라져 테스트가 무의미해진다.
const BODY = `
<div class="panel result-panel hero-panel">
  <div class="balls">
    <span class="ball">1</span><span class="ball">2</span><span class="ball">3</span>
    <span class="ball-separator">+</span><span class="ball bonus-ball">7</span>
  </div>
  <div class="prize-table-wrap">
    <table class="prize-table"><tbody>
      <tr>
        <th class="prize-table-rank">1등 당첨금</th>
        <td class="prize-table-amount">100</td>
        <td class="prize-table-after-tax">
          <span class="prize-table-after-tax-label">세후</span>
          <span class="prize-table-after-tax-value">90</span>
        </td>
      </tr>
    </tbody></table>
  </div>
</div>

<div class="panel">
  <div class="frequency-grid">
    <div class="frequency-item">1</div>
  </div>
</div>

<ul class="pattern-list">
  <li class="pattern-item">
    <span class="pattern-key">k</span><span class="bar-track"></span>
    <span class="pattern-count">1</span><span class="pattern-pct">1%</span>
  </li>
</ul>

<ol class="companion-list">
  <li class="companion-item"><span>1</span><span class="pair-info">a</span><span class="rank">1</span></li>
</ol>

<div class="page-with-sidebar">
  <div class="ad-sidebar">sidebar</div>
</div>
`;

type Snapshot = {
  ballsGap: string | undefined;
  prizeTdPadding: string | undefined;
  rankFontSize: string | undefined;
  amountFontSize: string | undefined;
  fgGap: string | undefined;
  piGap: string | undefined;
  piPadding: string | undefined;
  pcTextAlign: string | undefined;
  ppTextAlign: string | undefined;
  ciGap: string | undefined;
};

async function snapshot(page: import("@playwright/test").Page, width: number): Promise<Snapshot> {
  await page.setViewportSize({ width, height: 900 });
  await page.setContent(`<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>${BODY}</body></html>`);
  await page.addStyleTag({ content: CSS });

  return page.evaluate(() => {
    const cs = (sel: string) => {
      const el = document.querySelector(sel);
      return el ? getComputedStyle(el) : null;
    };
    const balls = cs(".result-panel .balls");
    const td = cs(".prize-table td");
    const rank = cs(".prize-table-rank");
    const amount = cs(".prize-table-amount");
    const fg = cs(".frequency-grid");
    const pi = cs(".pattern-item");
    const pc = cs(".pattern-count");
    const pp = cs(".pattern-pct");
    const ci = cs(".companion-item");
    return {
      ballsGap: balls?.columnGap,
      prizeTdPadding: td?.padding,
      rankFontSize: rank?.fontSize,
      amountFontSize: amount?.fontSize,
      fgGap: fg?.columnGap,
      piGap: pi?.columnGap,
      piPadding: pi?.padding,
      pcTextAlign: pc?.textAlign,
      ppTextAlign: pp?.textAlign,
      ciGap: ci?.columnGap,
    };
  });
}

// 골든 마스터: 390/768/1280px 각 너비에서 기대하는 계산값. .companion-item은
// ≥640px에서 gap이 부모 .companion-list의 gap(6px)을 상속하지 않고 자신의 값(14px)을
// 갖는지가 핵심 — 부모 gap을 잘못 상속하면 이 값이 6px로 나타난다.
test.describe("globals.css 컴퓨티드 스타일 회귀 방지", () => {
  test("390px(모바일)", async ({ page }) => {
    const s = await snapshot(page, 390);
    expect(s.ballsGap).toBe("6px");
    expect(s.prizeTdPadding).toBe("12px 10px");
    expect(s.fgGap).toBe("10px");
    expect(s.piGap).toBe("12px");
    expect(s.piPadding).toBe("12px");
    expect(s.pcTextAlign).toBe("left");
    expect(s.ppTextAlign).toBe("left");
    // 모바일은 1열 스택 레이아웃이라 10px이 의도값 — 14px은 ≥640px의 3열 가로
    // 레이아웃에서만 적용된다.
    expect(s.ciGap).toBe("10px");
  });

  test("768px(태블릿)", async ({ page }) => {
    const s = await snapshot(page, 768);
    expect(s.ballsGap).toBe("10px");
    expect(s.prizeTdPadding).toBe("16px");
    // font-size는 --fs-base(clamp(0.92rem, 0.87rem + 0.3vw, 0.98rem)) 토큰을 쓴다 —
    // ≥640px에서는 상한(0.98rem=15.68px)에 항상 닿는 게 의도된 값이다.
    expect(s.rankFontSize).toBe("15.68px");
    expect(s.amountFontSize).toBe("20px");
    expect(s.fgGap).toBe("12px");
    expect(s.piGap).toBe("10px");
    expect(s.piPadding).toBe("7px 10px");
    expect(s.pcTextAlign).toBe("right");
    expect(s.ppTextAlign).toBe("right");
    expect(s.ciGap).toBe("14px");
  });

  test("1280px(데스크톱)", async ({ page }) => {
    const s = await snapshot(page, 1280);
    expect(s.ballsGap).toBe("10px");
    expect(s.prizeTdPadding).toBe("16px");
    // font-size는 --fs-base(clamp(0.92rem, 0.87rem + 0.3vw, 0.98rem)) 토큰을 쓴다 —
    // ≥640px에서는 상한(0.98rem=15.68px)에 항상 닿는 게 의도된 값이다.
    expect(s.rankFontSize).toBe("15.68px");
    expect(s.amountFontSize).toBe("20px");
    expect(s.fgGap).toBe("12px");
    expect(s.piGap).toBe("10px");
    expect(s.piPadding).toBe("7px 10px");
    expect(s.pcTextAlign).toBe("right");
    expect(s.ppTextAlign).toBe("right");
    expect(s.ciGap).toBe("14px");
  });

  test("사이드바 광고 top이 헤더 높이만큼 오프셋된다 (1280px)", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.setContent(`<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body>${BODY}</body></html>`);
    await page.addStyleTag({ content: CSS });

    const top = await page.evaluate(() => {
      const el = document.querySelector(".ad-sidebar");
      return el ? getComputedStyle(el).top : null;
    });
    // --header-h(82px, ≥1024) + 24px. env(safe-area-inset-top)는 이 테스트 환경에서 0.
    expect(top).toBe("106px");
  });
});

// 브레이크포인트 값이 src/lib/breakpoints.ts(BP)와 globals.css 양쪽에 문자열로
// 흩어져 있어 CSS만 바꾸면 JS가 조용히 어긋날 수 있다. CSS가 실제로 쓰는 min-width
// 리터럴 집합이 BP의 값과 정확히 일치하는지 빌드 없이 정적으로 검증한다.
test("CSS의 min-width 브레이크포인트가 breakpoints.ts(BP)와 일치한다", async () => {
  const { BP } = await import("../src/lib/breakpoints");
  const literals = new Set(
    [...CSS.matchAll(/@media\s*\(min-width:\s*(\d+)px\)/g)].map((m) => Number(m[1])),
  );
  expect(literals.size).toBeGreaterThan(0);
  for (const value of literals) {
    expect([BP.tablet, BP.desktop]).toContain(value);
  }
  expect(literals).toContain(BP.tablet);
  expect(literals).toContain(BP.desktop);
});
