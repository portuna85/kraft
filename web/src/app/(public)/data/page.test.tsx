import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import DataHubPage from "./page";

/**
 * RSP-28(docs/improvement.md): 카드 4개의 링크 접근 이름이 전부 "보기"로
 * 같았다 — 스크린리더의 링크 목록 탐색(Tab 없이 링크만 나열)에서 목적지를
 * 구별할 수 없었다. axe의 `link-name` 규칙은 "이름이 있는가"만 보고 "고유한가"는
 * 보지 않아 `e2e/a11y`가 `/data`를 이미 스캔해도 이 결함은 못 잡는다 — 이
 * 유닛 테스트가 유일한 자동 방어선이다.
 */
describe("DataHubPage", () => {
  it("카드 4개의 링크 접근 이름이 서로 다르다", () => {
    render(<DataHubPage />);

    const links = screen.getAllByRole("link");
    // 접근 이름은 aria-label이 없으므로 텍스트 콘텐츠(sr-only 포함)에서 온다 —
    // jsdom 타입에 accessibleName이 없어 textContent로 같은 값을 얻는다.
    const names = links.map((link) => link.textContent?.trim().replace(/\s+/g, " "));

    expect(new Set(names).size).toBe(names.length);
  });

  it("각 카드 링크의 접근 이름이 해당 기능 제목을 포함한다", () => {
    render(<DataHubPage />);

    // InsightsHubNav 탭도 같은 제목 텍스트("출현 통계" 등)를 쓰므로, 카드 링크
    // 고유의 "보기" 접미(RSP-28 sr-only 조합)로 카드 링크만 골라낸다.
    for (const title of ["출현 통계", "패턴 통계", "동반 출현", "번호 분석"]) {
      expect(screen.getByRole("link", { name: new RegExp(`${title}.*보기`) })).toBeInTheDocument();
    }
  });
});
