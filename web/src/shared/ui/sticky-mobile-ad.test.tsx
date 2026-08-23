import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { StickyMobileAd } from "./sticky-mobile-ad";

// matchMedia mock — StickyMobileAd가 useMediaQuery(min-width:1024px, max-height:480px)로
// "이 뷰포트에서 아예 노출되는가"를 판단한다(콘텐츠 컬럼 폭과 무관). 쿼리별로 다른 값을
// 매치시켜야 하는 케이스(예: 데스크톱 아님 + 짧은 뷰포트)가 있어 단일 boolean이 아니라
// 쿼리 문자열 기준 predicate도 받는다.
function mockMatchMedia(matches: boolean | ((query: string) => boolean)) {
  const resolve = typeof matches === "function" ? matches : () => matches;
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: resolve(query),
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

beforeEach(() => {
  mockMatchMedia(false);
});

describe("모바일 하단 고정 배너", () => {
  afterEach(() => {
    document.documentElement.style.removeProperty("--fixed-bottom-inset");
  });

  it("unit이 비어있으면 아무것도 렌더링하지 않는다", () => {
    mockMatchMedia(false);
    const { container } = render(<StickyMobileAd unit="" />);

    expect(container).toBeEmptyDOMElement();
  });

  it("unit이 있으면 광고와 닫기 버튼을 렌더링하고 --fixed-bottom-inset을 올린다", () => {
    mockMatchMedia(false);
    render(<StickyMobileAd unit="DAN-sticky123" />);

    const ins = document.querySelector("ins.kakao_ad_area");
    expect(ins).toBeInTheDocument();
    expect(ins).toHaveAttribute("data-ad-unit", "DAN-sticky123");
    expect(screen.getByLabelText("광고 닫기")).toBeInTheDocument();
    // KF-06: 51px = 광고 콘텐츠 높이(50px) + border-top(1px) — 탭바 안전영역
    // 여백은 더 이상 여기 포함되지 않는다(탭바가 이미 흡수한다).
    expect(document.documentElement.style.getPropertyValue("--fixed-bottom-inset")).toBe("51px");
  });

  it("닫기 버튼을 클릭하면 배너가 사라지고 --fixed-bottom-inset이 0으로 돌아간다", () => {
    mockMatchMedia(false);
    const { container } = render(<StickyMobileAd unit="DAN-sticky123" />);

    fireEvent.click(screen.getByLabelText("광고 닫기"));

    expect(container).toBeEmptyDOMElement();
    expect(document.documentElement.style.getPropertyValue("--fixed-bottom-inset")).toBe("0px");
  });

  it("데스크톱 뷰포트에서는 mount하지 않는다(CSS로만 숨기면 SDK 요청이 남음)", () => {
    mockMatchMedia(true);
    const { container } = render(<StickyMobileAd unit="DAN-sticky123" />);

    expect(container).toBeEmptyDOMElement();
  });

  // F-06(과거 수정 배경): 예전엔 CSS의 @media (max-height: 480px)가
  // .adStickyMobile을 숨기는 걸로 이 케이스를 처리했었다 — 그 CSS 규칙은
  // 이후 제거됐고, 지금은 아래 matchMedia 게이트가 mount 자체를 막는
  // 유일한 시행 지점이다. 이 게이트가 없으면 광고는 안 보이는데도
  // --fixed-bottom-inset은 66px로 남아 세로 공간이 가장 부족한 가로모드
  // 화면에서 유령 여백이 생긴다.
  it("가로모드 등 짧은 뷰포트(max-height: 480px)에서는 mount하지 않고 인셋도 0으로 유지한다", () => {
    mockMatchMedia((query) => query === "(max-height: 480px)");
    const { container } = render(<StickyMobileAd unit="DAN-sticky123" />);

    expect(container).toBeEmptyDOMElement();
    expect(document.documentElement.style.getPropertyValue("--fixed-bottom-inset")).toBe("0px");
  });
});
