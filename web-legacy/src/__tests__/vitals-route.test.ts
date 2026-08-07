import { beforeEach, describe, expect, it, vi } from "vitest";
import { resetRateLimitForTests } from "@/lib/rate-limit";

const infoSpy = vi.fn();

vi.mock("@/lib/logger", () => ({
  default: { info: (...args: unknown[]) => infoSpy(...args) },
}));

// H-2: 샘플링이 로깅을 확률적으로 건너뛴다 — 로깅 자체를 검증하는 테스트가 흔들리지
// 않도록 항상 샘플에 포함되게 고정한다(수용/거부 판정 자체는 샘플링과 무관하다).
vi.spyOn(Math, "random").mockReturnValue(0);

function request(body: unknown, headers?: Record<string, string>) {
  return new Request("http://localhost/api/vitals", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
    headers,
  }) as unknown as import("next/server").NextRequest;
}

const VALID_PAYLOAD = {
  name: "LCP",
  value: 1234.5,
  rating: "good",
  route: "/recommend",
  deviceClass: "mobile",
  release: "abc123",
};

describe("POST /api/vitals", () => {
  beforeEach(() => {
    infoSpy.mockClear();
    resetRateLimitForTests();
  });

  it("정상 페이로드는 204를 반환하고 화이트리스트된 필드만 로그로 남긴다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request(VALID_PAYLOAD));

    expect(res.status).toBe(204);
    expect(infoSpy).toHaveBeenCalledWith(
      {
        name: "LCP",
        value: 1234.5,
        rating: "good",
        route: "/recommend",
        deviceClass: "mobile",
        release: "abc123",
      },
      "web-vitals"
    );
  });

  it("로그 인자에 개인정보로 이어질 수 있는 필드(ip, userAgent, cookie 등)를 절대 포함하지 않는다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    await POST(request(VALID_PAYLOAD));

    const loggedArgs = infoSpy.mock.calls[0]![0];
    const keys = Object.keys(loggedArgs);
    expect(keys.sort()).toEqual(["deviceClass", "name", "rating", "release", "route", "value"]);
    expect(loggedArgs).not.toHaveProperty("ip");
    expect(loggedArgs).not.toHaveProperty("userAgent");
    expect(loggedArgs).not.toHaveProperty("cookie");
    expect(loggedArgs).not.toHaveProperty("userId");
  });

  it("지원하지 않는 metric name은 400을 반환하고 로그를 남기지 않는다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request({ ...VALID_PAYLOAD, name: "FID" }));

    expect(res.status).toBe(400);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("value가 음수이거나 숫자가 아니면 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const negRes = await POST(request({ ...VALID_PAYLOAD, value: -1 }));
    const nanRes = await POST(request({ ...VALID_PAYLOAD, value: "not-a-number" }));

    expect(negRes.status).toBe(400);
    expect(nanRes.status).toBe(400);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("rating·deviceClass가 화이트리스트 밖이면 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const badRating = await POST(request({ ...VALID_PAYLOAD, rating: "excellent" }));
    const badDevice = await POST(request({ ...VALID_PAYLOAD, deviceClass: "smart-fridge" }));

    expect(badRating.status).toBe(400);
    expect(badDevice.status).toBe(400);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("route가 지나치게 길면(악성/오류 입력 방어) 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request({ ...VALID_PAYLOAD, route: "/" + "a".repeat(300) }));

    expect(res.status).toBe(400);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("JSON이 아닌 본문(malformed)은 400을 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request("not json"));

    expect(res.status).toBe(400);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  // H-2: 이 라우트는 백엔드 PublicRateLimitFilter가 도달할 수 없어 자체 방어가 필요하다.
  it("Content-Length가 상한을 넘으면 본문을 읽지 않고 413을 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request(VALID_PAYLOAD, { "content-length": "999999" }));

    expect(res.status).toBe(413);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("허용되지 않은 Content-Type은 415를 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request(VALID_PAYLOAD, { "content-type": "multipart/form-data" }));

    expect(res.status).toBe(415);
    expect(infoSpy).not.toHaveBeenCalled();
  });

  it("한 IP가 분당 한도를 넘으면 429를 반환한다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    const headers = { "x-forwarded-for": "203.0.113.9" };
    let lastStatus = 0;
    for (let i = 0; i < 61; i++) {
      lastStatus = (await POST(request(VALID_PAYLOAD, headers))).status;
    }

    expect(lastStatus).toBe(429);
  });

  it("서로 다른 IP는 서로의 속도 제한에 영향을 주지 않는다", async () => {
    const { POST } = await import("@/app/api/vitals/route");
    for (let i = 0; i < 60; i++) {
      await POST(request(VALID_PAYLOAD, { "x-forwarded-for": "203.0.113.10" }));
    }
    const res = await POST(request(VALID_PAYLOAD, { "x-forwarded-for": "203.0.113.11" }));

    expect(res.status).toBe(204);
  });

  it("샘플링 확률 밖이면 유효한 요청도 로그를 남기지 않는다", async () => {
    vi.spyOn(Math, "random").mockReturnValueOnce(0.99);
    const { POST } = await import("@/app/api/vitals/route");
    const res = await POST(request(VALID_PAYLOAD));

    expect(res.status).toBe(204);
    expect(infoSpy).not.toHaveBeenCalled();
  });
});
