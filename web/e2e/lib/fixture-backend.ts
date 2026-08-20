/**
 * 픽스처 백엔드(`e2e/fixtures/backend.mjs`) URL을 프론트엔드 `baseURL`에서
 * 유도한다.
 *
 * 모든 e2e 트랙 설정(`playwright.*.config.ts`)이 `BACKEND_PORT = PORT + 1000`
 * 관례를 지킨다 — 트랙마다 포트를 겹치지 않게 고르면서도 백엔드 포트를 따로
 * 넘길 필요가 없게 하기 위해서다. 스펙 파일이 `http://127.0.0.1:4111`처럼
 * 포트를 하드코딩하면 그 값을 쓰는 트랙(`playwright.default.config.ts`)
 * 밖에서는 항상 `ECONNREFUSED`가 난다 — cross-browser 트랙에 `e2e/default`를
 * 재사용하려다 실제로 이 문제가 드러났다.
 */
export function fixtureBackendUrl(baseURL: string | undefined): string {
  if (!baseURL) {
    throw new Error(
      "fixtureBackendUrl: baseURL이 설정되지 않았다 (playwright config의 use.baseURL 확인)",
    );
  }
  const url = new URL(baseURL);
  const backendPort = Number(url.port) + 1000;
  return `${url.protocol}//${url.hostname}:${backendPort}`;
}
