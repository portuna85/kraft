import { beforeEach, describe, expect, it, vi } from "vitest";

const infoSpy = vi.fn();

vi.mock("@/lib/logger", () => ({
  default: { info: (...args: unknown[]) => infoSpy(...args) },
}));

function request(body: unknown) {
  return new Request("http://localhost/api/vitals", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
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

    const loggedArgs = infoSpy.mock.calls[0][0];
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
});
