import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * `/ops` 호스트 게이트 단위 테스트
 *
 * `e2e/proxy/ops-gate.spec.ts`는 "env 미설정 → 404"만 검증한다. 그런데 체크리스트가
 * 요구하는 "공개 도메인에서 접근 불가"의 실제 시나리오는 **env가 설정된 상태에서
 * 엉뚱한 Host로 요청이 들어오는 경우**다(운영 환경은 보통 env가 설정돼 있다) — 그
 * 케이스는 지금까지 어디에도 없었다.
 *
 * `serverEnv.opsAllowedHost`가 모듈 로드 시점에 `process.env`를 한 번 읽어 고정하므로
 * (`shared/config/env.ts:15`), 케이스마다 env를 바꾼 뒤 모듈을 다시 로드해야 한다.
 */
function opsRequest(host: string): NextRequest {
  return new NextRequest("http://placeholder.invalid/ops", { headers: { host } });
}

beforeEach(() => {
  delete process.env.KRAFT_OPS_ALLOWED_HOST;
});

afterEach(() => {
  delete process.env.KRAFT_OPS_ALLOWED_HOST;
});

async function loadProxy() {
  vi.resetModules();
  const mod = await import("./proxy");
  return mod.proxy;
}

describe("proxy — /ops 호스트 게이트", () => {
  it("KRAFT_OPS_ALLOWED_HOST 미설정이면 404다", async () => {
    const proxy = await loadProxy();
    const response = proxy(opsRequest("localhost"));
    expect(response.status).toBe(404);
  });

  it("설정된 값과 Host가 일치하면 통과한다", async () => {
    process.env.KRAFT_OPS_ALLOWED_HOST = "localhost";
    const proxy = await loadProxy();
    const response = proxy(opsRequest("localhost"));
    expect(response.status).not.toBe(404);
  });

  it("설정돼 있어도 Host가 다르면 404다 (공개 도메인에서 접근 불가)", async () => {
    process.env.KRAFT_OPS_ALLOWED_HOST = "localhost";
    const proxy = await loadProxy();
    const response = proxy(opsRequest("evil.example.com"));
    expect(response.status).toBe(404);
  });

  it("Host의 포트는 비교에서 무시한다", async () => {
    process.env.KRAFT_OPS_ALLOWED_HOST = "localhost";
    const proxy = await loadProxy();
    const response = proxy(opsRequest("localhost:3000"));
    expect(response.status).not.toBe(404);
  });
});
