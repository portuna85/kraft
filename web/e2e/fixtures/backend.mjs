// E2E용 최소 백엔드 픽스처.
//
// 실제 백엔드를 띄우지 않고도 RSC 라우트가 렌더되게 한다. 응답은 Valibot 스키마를
// 만족하는 최소값이며, **계약과 어긋나면 스키마가 막아 준다** — 픽스처가 슬금슬금
// 실제 계약에서 멀어지는 것을 그 자리에서 알 수 있다(§21.7과 같은 취지).
//
//   PORT=4110 node e2e/fixtures/backend.mjs
//
// L-01: 도메인별 픽스처 데이터·라우트는 ./domains/*.mjs로
// 나눠져 있다. 이 파일은 그것들을 하나로 합쳐 단일 HTTP 서버로 서빙하는 진입점
// 역할만 한다 — 여러 시작점을 만들지 않는다.
import { createServer } from "node:http";

import * as community from "./domains/community.mjs";
import * as numbers from "./domains/numbers.mjs";
import * as stats from "./domains/stats.mjs";

const PORT = Number(process.env.PORT ?? 4110);

const ROUTES = new Map([...stats.routes, ...community.routes, ...numbers.routes]);

const DYNAMIC_ROUTES = [
  ...stats.dynamicRoutes,
  ...community.dynamicRoutes,
  ...numbers.dynamicRoutes,
];

/** T-20(핵심 데이터 실패 → 5xx) 검증용 — 테스트가 이 경로로 특정 경로를 고장낼 수 있다. */
let forcedFailurePath = null;

const server = createServer(async (request, response) => {
  const url = new URL(request.url ?? "/", `http://127.0.0.1:${PORT}`);
  const method = request.method ?? "GET";

  // XSRF-TOKEN 쿠키 — 실제 백엔드(Spring Security)가 GET 응답마다 내려주는 것과 같은
  // 역할이다. 이게 없으면 browserMutate가 요청을 보내기 전에 스스로 막는다(§13.4).
  response.setHeader("set-cookie", "XSRF-TOKEN=e2e-fixture-token; Path=/");
  response.setHeader("content-type", "application/json");

  if (url.pathname === "/__test__/fail" && method === "PUT") {
    forcedFailurePath = url.searchParams.get("path");
    response.statusCode = 204;
    response.end();
    return;
  }
  if (url.pathname === "/__test__/reset" && method === "POST") {
    forcedFailurePath = null;
    Object.assign(stats.LATEST_ROUND, stats.BASE_LATEST_ROUND);
    response.statusCode = 204;
    response.end();
    return;
  }
  if (url.pathname === "/__test__/latest" && method === "PUT") {
    const round = Number(url.searchParams.get("round"));
    const drawDate = url.searchParams.get("drawDate");
    if (!Number.isInteger(round) || drawDate === null) {
      response.statusCode = 400;
      response.end(JSON.stringify({ code: "INVALID_TEST_INPUT" }));
      return;
    }
    Object.assign(stats.LATEST_ROUND, { round, drawDate });
    response.statusCode = 204;
    response.end();
    return;
  }

  if (forcedFailurePath !== null && url.pathname === forcedFailurePath) {
    response.statusCode = 503;
    response.end(JSON.stringify({ code: "FORCED_FAILURE", message: "픽스처가 강제한 실패." }));
    return;
  }

  let requestBody = null;
  if (method === "POST" || method === "PUT") {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const raw = Buffer.concat(chunks).toString("utf8");
    if (raw.length > 0) requestBody = JSON.parse(raw);
  }

  const dynamic = DYNAMIC_ROUTES.find(
    (route) => route.method === method && route.pattern.test(url.pathname),
  );
  if (dynamic !== undefined) {
    const [, id] = url.pathname.match(dynamic.pattern);
    const { status, body } = dynamic.handle(id);
    response.statusCode = status;
    response.end(body === null ? undefined : JSON.stringify(body));
    return;
  }

  const entry = ROUTES.get(url.pathname);
  if (entry === undefined) {
    response.statusCode = 404;
    response.end(
      JSON.stringify({ code: "NOT_FOUND", message: `픽스처에 없는 경로: ${url.pathname}` }),
    );
    return;
  }

  // 쿼리나 본문, 메서드에 따라 응답이 달라지는 경로는 함수로 둔다.
  const body = typeof entry === "function" ? entry(url.searchParams, requestBody, method) : entry;

  response.statusCode = 200;
  response.end(JSON.stringify(body));
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[fixture-backend] http://127.0.0.1:${PORT}`);
});
