import { afterEach, describe, expect, it, vi } from "vitest";
import logger, { logCoreDataFailure, serializeError } from "@/lib/logger";

describe("serializeError", () => {
  it("DOMException의 진단 필드만 남기고 열거 가능한 상수는 제외한다", () => {
    const serialized = serializeError(
      new DOMException("The operation timed out", "TimeoutError")
    );

    expect(serialized).toMatchObject({
      type: "DOMException",
      name: "TimeoutError",
      message: "The operation timed out",
    });
    expect(serialized).not.toHaveProperty("INDEX_SIZE_ERR");
    expect(serialized).not.toHaveProperty("TIMEOUT_ERR");
  });

  it("Error cause는 한 단계까지만 안전하게 직렬화한다", () => {
    const rootCause = new Error("connection refused", {
      cause: new Error("nested"),
    });
    const error = new TypeError("fetch failed", { cause: rootCause });

    const serialized = serializeError(error);

    expect(serialized.type).toBe("TypeError");
    expect(serialized.cause).toMatchObject({
      type: "Error",
      message: "connection refused",
    });
    expect(serialized.cause).not.toHaveProperty("cause");
  });
});

describe("logCoreDataFailure", () => {
  const originalPhase = process.env.NEXT_PHASE;

  afterEach(() => {
    process.env.NEXT_PHASE = originalPhase;
    vi.restoreAllMocks();
  });

  it("빌드 단계(NEXT_PHASE=phase-production-build)에서는 error가 아니라 warn으로 남긴다", () => {
    process.env.NEXT_PHASE = "phase-production-build";
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined);
    const errorSpy = vi.spyOn(logger, "error").mockImplementation(() => undefined);

    logCoreDataFailure(new Error("boom"), "핵심 데이터 조회 실패");

    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy).not.toHaveBeenCalled();
    expect(warnSpy.mock.calls[0][1]).toContain("[빌드 타임 폴백]");
  });

  it("빌드 단계가 아니면(런타임) 그대로 error로 남긴다", () => {
    process.env.NEXT_PHASE = "phase-production-server";
    const warnSpy = vi.spyOn(logger, "warn").mockImplementation(() => undefined);
    const errorSpy = vi.spyOn(logger, "error").mockImplementation(() => undefined);

    logCoreDataFailure(new Error("boom"), "핵심 데이터 조회 실패");

    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy).not.toHaveBeenCalled();
    expect(errorSpy.mock.calls[0][1]).toBe("핵심 데이터 조회 실패");
  });
});
