import { describe, expect, it } from "vitest";
import { formatCurrency, formatDateTime, formatDrawDate } from "@/lib/format";

describe("통화 형식", () => {
  it("0원을 그대로 표기한다", () => {
    expect(formatCurrency(0)).toBe("0원");
  });

  it("억 단위를 한국식 천 단위 콤마로 구분한다", () => {
    expect(formatCurrency(2_100_000_000)).toBe("2,100,000,000원");
  });

  it("1등 당첨금 형태의 금액을 포맷한다", () => {
    expect(formatCurrency(1_500_000_000)).toBe("1,500,000,000원");
  });
});

describe("추첨일 형식", () => {
  it("연월일 문자열을 파싱해 한국어 날짜 문자열을 반환한다", () => {
    const result = formatDrawDate("2026-06-07");
    expect(result).toContain("2026");
    expect(result).toContain("6");
    expect(result).toContain("7");
  });

  it("최초 추첨일에도 예외를 던지지 않는다", () => {
    expect(() => formatDrawDate("2002-12-07")).not.toThrow();
  });

  it("호스트 시간대와 무관하게 날짜를 한국 표준시로 고정한다", () => {
    // 입력은 날짜만 있는 YYYY-MM-DD이고, format.ts가 +09:00으로 고정하므로
    // 다른 타임존에서 실행해도 렌더링된 일자가 6일/8일로 밀리지 않는다.
    expect(formatDrawDate("2026-06-07")).toContain("7");
  });
});

describe("일시 형식", () => {
  it("시간대가 포함된 시각을 24시간제 한국 표준시로 렌더링한다", () => {
    // 2026-06-07T15:30:00Z == 2026-06-08 00:30:00 KST.
    const result = formatDateTime("2026-06-07T15:30:00Z");
    expect(result).toContain("2026");
    expect(result).toContain("8");
    expect(result).toContain("00:30");
  });
});
