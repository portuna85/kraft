import { describe, expect, it } from "vitest";
import { ballColorClass } from "@/lib/ball-color";

describe("공 색상 클래스", () => {
  it("1-10 구간은 빈 문자열(노랑/기본)을 반환한다", () => {
    expect(ballColorClass(1)).toBe("");
    expect(ballColorClass(10)).toBe("");
  });

  it("11-20 구간은 파란색 클래스를 반환한다", () => {
    expect(ballColorClass(11)).toBe("ball-blue");
    expect(ballColorClass(20)).toBe("ball-blue");
  });

  it("21-30 구간은 빨간색 클래스를 반환한다", () => {
    expect(ballColorClass(21)).toBe("ball-red");
    expect(ballColorClass(30)).toBe("ball-red");
  });

  it("31-40 구간은 회색 클래스를 반환한다", () => {
    expect(ballColorClass(31)).toBe("ball-gray");
    expect(ballColorClass(40)).toBe("ball-gray");
  });

  it("41-45 구간은 초록색 클래스를 반환한다", () => {
    expect(ballColorClass(41)).toBe("ball-green");
    expect(ballColorClass(45)).toBe("ball-green");
  });
});
