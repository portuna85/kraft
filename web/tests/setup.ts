import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(() => {
  cleanup();
});

// jsdom은 ResizeObserver를 구현하지 않는다(I-14) — 이걸 쓰는 컴포넌트를 렌더하는
// 테스트가 매번 각자 모킹하지 않아도 되도록 여기서 전역 no-op 폴리필을 둔다. resize
// 콜백 자체를 검증해야 하는 테스트(예: use-grid-column-count.test.tsx, ad-unit.test.tsx)는
// beforeEach에서 vi.stubGlobal("ResizeObserver", ...)로 이 기본값을 그 테스트 파일
// 범위에서만 덮어쓴다.
if (typeof globalThis.ResizeObserver === "undefined") {
  class NoopResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = NoopResizeObserver as unknown as typeof ResizeObserver;
}

// jsdom은 Element.scrollIntoView를 구현하지 않는다(UX-03) — 새 댓글로 스크롤하는
// 컴포넌트를 렌더하면 이펙트 안에서 호출이 그대로 터진다. 스크롤 자체를 검증하는
// 테스트는 없으므로 전역 no-op이면 충분하다.
if (typeof Element.prototype.scrollIntoView !== "function") {
  Element.prototype.scrollIntoView = function scrollIntoView() {};
}
