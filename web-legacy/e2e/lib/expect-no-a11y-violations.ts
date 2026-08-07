import { expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

// F-01: WCAG 2.2 AA 자동 검사. axe는 색 대비·랜드마크·이름/설명·폼 오류 연결처럼
// 기계로 잡을 수 있는 항목만 잡는다 — 키보드 순회, 포커스 이동, 스크린리더 낭독
// 순서는 여전히 수동 점검이 필요하다(자동화가 대체하지 못함).
export async function expectNoA11yViolations(page: Page, disableRules: string[] = []) {
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .disableRules(disableRules)
    .analyze();

  const summary = results.violations.map((v) => ({
    id: v.id,
    impact: v.impact,
    help: v.help,
    nodes: v.nodes.map((n) => n.target.join(" ")),
  }));

  expect(summary, JSON.stringify(summary, null, 2)).toEqual([]);
}
