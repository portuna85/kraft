import { describe, expect, it } from "vitest";

import { isInAppBrowser } from "./in-app-browser";

describe("인앱 브라우저 감지", () => {
  it("카카오톡 인앱 브라우저를 감지한다", () => {
    expect(
      isInAppBrowser(
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) KAKAOTALK",
      ),
    ).toBe(true);
  });

  it("인스타그램 인앱 브라우저를 감지한다", () => {
    expect(
      isInAppBrowser("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Instagram 300.0.0.0"),
    ).toBe(true);
  });

  it("일반 데스크톱 Chrome은 감지하지 않는다", () => {
    expect(
      isInAppBrowser(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
      ),
    ).toBe(false);
  });

  it("일반 모바일 Safari는 감지하지 않는다", () => {
    expect(
      isInAppBrowser(
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
      ),
    ).toBe(false);
  });
});
