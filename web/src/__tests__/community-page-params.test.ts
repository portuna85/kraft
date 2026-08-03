import { describe, expect, it } from "vitest";
import { normalizeQuery, parsePage } from "@/app/community/page";

// M-11: `Math.max(0, Number(pageParam) || 0)`는 Number.isInteger/상한 검사가 없어
// ?page=Infinity, ?page=1.5, ?page=1e21이 그대로 Spring int/Pageable 파라미터로
// 나가 400/500이 됐다. 유한한 안전 정수만 통과하는지 검증한다.
describe("커뮤니티 페이지 파라미터 파싱(M-11)", () => {
  describe("parsePage", () => {
    it("정상적인 페이지 번호를 그대로 받아들인다", () => {
      expect(parsePage("3")).toBe(3);
    });

    it("파라미터가 없으면 0페이지다", () => {
      expect(parsePage(undefined)).toBe(0);
    });

    it("Infinity는 0페이지로 되돌린다", () => {
      expect(parsePage("Infinity")).toBe(0);
    });

    it("소수는 0페이지로 되돌린다", () => {
      expect(parsePage("1.5")).toBe(0);
    });

    it("음수는 0페이지로 되돌린다", () => {
      expect(parsePage("-3")).toBe(0);
    });

    it("숫자가 아닌 값은 0페이지로 되돌린다", () => {
      expect(parsePage("abc")).toBe(0);
    });

    it("천문학적으로 큰 정수(1e21)는 상한으로 clamp한다", () => {
      expect(parsePage("1e21")).toBe(100_000);
    });
  });

  describe("normalizeQuery", () => {
    it("정상 범위의 검색어를 그대로 받아들인다", () => {
      expect(normalizeQuery("로또")).toBe("로또");
    });

    it("파라미터가 없으면 undefined다", () => {
      expect(normalizeQuery(undefined)).toBeUndefined();
    });

    it("검색창 minLength(2자)보다 짧으면 undefined다(직접 URL로 HTML 제약 우회 방어)", () => {
      expect(normalizeQuery("a")).toBeUndefined();
    });

    it("공백만 있으면 undefined다", () => {
      expect(normalizeQuery("   ")).toBeUndefined();
    });

    it("검색창 maxLength(50자)보다 길면 잘라낸다(직접 URL로 HTML 제약 우회 방어)", () => {
      const long = "a".repeat(300);
      const result = normalizeQuery(long);
      expect(result).toHaveLength(50);
    });
  });
});
