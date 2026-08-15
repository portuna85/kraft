import { describe, expect, it } from "vitest";

import { countdownUntil, formatCountdown, nextDrawTime } from "./draw-schedule";

describe("다음 추첨 시각", () => {
  it("주중이면 같은 주 토요일 20시 35분(KST)을 가리킨다", () => {
    // 2026-08-12(수) 00:00 UTC → 2026-08-15(토) 11:35 UTC = KST 20:35
    const next = nextDrawTime(new Date("2026-08-12T00:00:00Z"));
    expect(next.toISOString()).toBe("2026-08-15T11:35:00.000Z");
  });

  it("추첨 직전이면 아직 같은 날 추첨을 가리킨다", () => {
    const next = nextDrawTime(new Date("2026-08-15T11:34:59Z"));
    expect(next.toISOString()).toBe("2026-08-15T11:35:00.000Z");
  });

  it("추첨 시각 정각이면 다음 주로 넘긴다", () => {
    // 이미 뽑힌 회차를 "0초 남음"으로 계속 보여주지 않는다.
    const next = nextDrawTime(new Date("2026-08-15T11:35:00Z"));
    expect(next.toISOString()).toBe("2026-08-22T11:35:00.000Z");
  });

  it("추첨 직후 토요일 밤이면 다음 주 토요일을 가리킨다", () => {
    const next = nextDrawTime(new Date("2026-08-15T13:00:00Z"));
    expect(next.toISOString()).toBe("2026-08-22T11:35:00.000Z");
  });

  it("브라우저 시간대와 무관하게 같은 시각을 낸다", () => {
    // 같은 순간을 다른 오프셋 표기로 줘도 결과가 같아야 한다.
    const fromUtc = nextDrawTime(new Date("2026-08-12T00:00:00Z"));
    const fromKst = nextDrawTime(new Date("2026-08-12T09:00:00+09:00"));
    expect(fromKst.getTime()).toBe(fromUtc.getTime());
  });
});

describe("남은 시간 계산", () => {
  it("일·시·분·초로 쪼갠다", () => {
    const result = countdownUntil(
      new Date("2026-08-15T11:35:00Z"),
      new Date("2026-08-12T00:00:00Z"),
    );
    expect(result).toEqual({ days: 3, hours: 11, minutes: 35, seconds: 0 });
  });

  it("이미 지난 시각이면 전부 0이다", () => {
    const result = countdownUntil(
      new Date("2026-08-15T11:35:00Z"),
      new Date("2026-08-16T00:00:00Z"),
    );
    expect(result).toEqual({ days: 0, hours: 0, minutes: 0, seconds: 0 });
  });
});

describe("남은 시간 문자열", () => {
  it("일수가 남아 있으면 일 단위부터 보여준다", () => {
    expect(formatCountdown({ days: 3, hours: 14, minutes: 22, seconds: 10 })).toBe(
      "3일 14시간 22분 10초",
    );
  });

  it("당일이면 일 단위를 생략한다", () => {
    expect(formatCountdown({ days: 0, hours: 5, minutes: 2, seconds: 9 })).toBe("5시간 2분 9초");
  });
});
