import { describe, expect, it } from "vitest";

import { REPORT_REASON_LABELS, REPORT_REASONS, REPORT_TARGET_TYPES } from "./schema";

describe("신고 사유 계약", () => {
  it("모든 사유에 라벨이 있다", () => {
    for (const reason of REPORT_REASONS) {
      expect(REPORT_REASON_LABELS[reason]).toBeTruthy();
    }
  });

  it("라벨에 없는 사유는 없다", () => {
    expect(Object.keys(REPORT_REASON_LABELS).sort()).toEqual([...REPORT_REASONS].sort());
  });

  it("신고 대상 유형은 게시글·댓글·사용자 3종이다", () => {
    expect(REPORT_TARGET_TYPES).toEqual(["POST", "COMMENT", "USER"]);
  });
});

// 생성 타입 정합성 체크 없음 — M-07: 이 파일은 요청 측 열거값
// (REPORT_REASONS/REPORT_TARGET_TYPES)만 정의하고, 응답을 파싱하는 Valibot 객체
// 스키마가 없다. 대응할 생성 타입 자체가 없어 `extends` 패리티 체크를 붙일 대상이 없다.
