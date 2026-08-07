import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.fn();
const revalidateTag = vi.fn();

vi.mock("next/cache", () => ({
  revalidatePath: (path: string) => revalidatePath(path),
  revalidateTag: (tag: string, profile: string) => revalidateTag(tag, profile),
}));

function request(body: unknown, secret = "test-secret") {
  return new Request("http://localhost/api/revalidate", {
    method: "POST",
    headers: { "X-Revalidate-Secret": secret, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  }) as unknown as import("next/server").NextRequest;
}

describe("revalidate 웹훅", () => {
  const originalSecret = process.env.KRAFT_REVALIDATE_SECRET;

  beforeEach(() => {
    vi.resetModules();
    revalidatePath.mockClear();
    revalidateTag.mockClear();
    process.env.KRAFT_REVALIDATE_SECRET = "test-secret";
  });

  afterEach(() => {
    process.env.KRAFT_REVALIDATE_SECRET = originalSecret;
  });

  it("시크릿이 일치하지 않으면 401을 반환한다", async () => {
    const { POST } = await import("@/app/api/revalidate/route");
    const response = await POST(request({ paths: ["/"] }, "wrong-secret"));

    expect(response.status).toBe(401);
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("허용된 태그는 revalidateTag를 호출한다", async () => {
    const { POST } = await import("@/app/api/revalidate/route");
    const response = await POST(request({ tags: ["rounds:latest", "stats:all"] }));

    expect(response.status).toBe(200);
    expect(revalidateTag).toHaveBeenCalledWith("rounds:latest", "max");
    expect(revalidateTag).toHaveBeenCalledWith("stats:all", "max");
    expect(revalidateTag).toHaveBeenCalledTimes(2);
  });

  it("허용되지 않은 태그는 무시한다", async () => {
    const { POST } = await import("@/app/api/revalidate/route");
    const response = await POST(request({ tags: ["not-a-real-tag", "rounds:detail:1200"] }));
    const body = await response.json();

    expect(revalidateTag).not.toHaveBeenCalled();
    expect(body.tags).toEqual([]);
  });

  it("path와 tag가 함께 오면 둘 다 처리한다(전환기 병행)", async () => {
    const { POST } = await import("@/app/api/revalidate/route");
    const response = await POST(request({ paths: ["/frequency"], tags: ["stats:all"] }));
    const body = await response.json();

    expect(revalidatePath).toHaveBeenCalledWith("/frequency");
    expect(revalidateTag).toHaveBeenCalledWith("stats:all", "max");
    expect(body).toMatchObject({ revalidated: true, paths: ["/frequency"], tags: ["stats:all"] });
  });
});
