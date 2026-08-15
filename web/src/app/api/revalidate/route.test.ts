import type { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const cacheMocks = vi.hoisted(() => ({
  revalidatePath: vi.fn(),
  revalidateTag: vi.fn(),
}));

vi.mock("next/cache", () => cacheMocks);
vi.mock("@/shared/config/env", () => ({
  serverEnv: { revalidateSecret: "test-revalidate-secret" },
}));

import { POST } from "./route";

function request(body: string, secret = "test-revalidate-secret"): NextRequest {
  return new Request("http://localhost/api/revalidate", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Revalidate-Secret": secret,
    },
    body,
  }) as NextRequest;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("POST /api/revalidate", () => {
  it("시크릿이 틀리면 본문을 처리하지 않고 401로 거부한다", async () => {
    const response = await POST(request("not-json", "wrong-secret"));

    expect(response.status).toBe(401);
    expect(await response.text()).toBe("Unauthorized");
    expect(cacheMocks.revalidatePath).not.toHaveBeenCalled();
    expect(cacheMocks.revalidateTag).not.toHaveBeenCalled();
  });

  it("인증됐어도 JSON이 아니면 400으로 거부한다", async () => {
    const response = await POST(request("not-json"));

    expect(response.status).toBe(400);
    expect(await response.text()).toBe("Invalid JSON body");
  });

  it("화이트리스트에 있는 경로와 태그만 무효화하고 실제 처리 목록을 반환한다", async () => {
    const response = await POST(
      request(
        JSON.stringify({
          paths: ["/", "/frequency", "/community/posts/1"],
          tags: ["rounds:latest", "stats:all", "unknown"],
        }),
      ),
    );

    expect(response.status).toBe(200);
    expect(cacheMocks.revalidateTag).toHaveBeenNthCalledWith(1, "rounds:latest", { expire: 0 });
    expect(cacheMocks.revalidateTag).toHaveBeenNthCalledWith(2, "stats:all", { expire: 0 });
    expect(cacheMocks.revalidatePath).toHaveBeenNthCalledWith(1, "/");
    expect(cacheMocks.revalidatePath).toHaveBeenNthCalledWith(2, "/frequency");
    await expect(response.json()).resolves.toEqual({
      revalidated: true,
      paths: ["/", "/frequency"],
      tags: ["rounds:latest", "stats:all"],
    });
  });

  it("배열이 아닌 입력은 안전하게 빈 처리 목록으로 정규화한다", async () => {
    const response = await POST(request(JSON.stringify({ paths: "/", tags: null })));

    expect(response.status).toBe(200);
    expect(cacheMocks.revalidatePath).not.toHaveBeenCalled();
    expect(cacheMocks.revalidateTag).not.toHaveBeenCalled();
    await expect(response.json()).resolves.toMatchObject({ paths: [], tags: [] });
  });
});
