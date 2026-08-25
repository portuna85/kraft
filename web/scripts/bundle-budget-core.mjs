// bundle-budget.mjs의 순수 계산부 — 파일시스템·process를 건드리지 않는다.
//
// PERF-BUNDLE-01(docs/improvement.md): 허용치 산술과 청크 분류에 실제 판단이 들어가면서
// 테스트가 필요해졌다. I/O가 섞인 채로는 테스트할 수 없어 여기로 분리했다.

/** manifest 파일의 상대 경로 → Next 내부 entry 키에 쓰이는 경로("(public)/page" 등). */
export function entryPathOf(relativeManifestPath) {
  return relativeManifestPath.replaceAll("\\", "/").replace(/_client-reference-manifest\.js$/, "");
}

/**
 * 라우트 이름. 라우트 그룹 세그먼트는 URL에 나타나지 않으므로 제거한다 —
 * `(public)/page`는 `/`, `(ops)/ops/page`는 `/ops`다. 예산 키는 URL 기준이라
 * 이걸 안 벗기면 실제 라우트와 매칭되지 않는다.
 */
export function routeOf(entryPath) {
  const segments = entryPath
    .split("/")
    .filter((segment) => segment !== "" && segment !== "page" && !/^\(.+\)$/.test(segment));
  return `/${segments.join("/")}`;
}

/**
 * Next가 파일 경로와 다르게 이름 붙이는 entry들. `app/not-found.tsx`는 manifest가
 * `_not-found/page_client-reference-manifest.js`로 나오지만 entry 키는
 * `[project]/src/app/not-found`다 — 이 차이 때문에 `/_not-found`가 오랫동안 자기 키를
 * 못 찾아 바닥값으로 측정됐다(PERF-BUNDLE-01).
 */
const SPECIAL_ENTRY_NAMES = { "_not-found/page": "not-found" };

/**
 * 이 manifest가 나타내는 페이지 자신의 entry 키를 고른다. 경로를 문자열로 조립해
 * 맞히려 들면 Next가 키를 정규화하는 방식(라우트 그룹·병렬 라우트)에 따라 조용히
 * 빗나가고, 그러면 모든 라우트가 레이아웃 바닥값으로 측정돼 예산이 무의미해진다.
 * 그래서 특수 이름도 **키가 실제로 존재할 때만** 채택한다.
 */
export function ownEntryKey(entryPath, entries) {
  const exact = Object.keys(entries).find((key) => key === `[project]/src/app/${entryPath}`);
  if (exact) return exact;

  const special = SPECIAL_ENTRY_NAMES[entryPath];
  if (special && `[project]/src/app/${special}` in entries) {
    return `[project]/src/app/${special}`;
  }

  const pageKeys = Object.keys(entries).filter((key) => key.endsWith("/page"));
  return pageKeys.length === 1 ? pageKeys[0] : null;
}

/**
 * 회귀 허용치. 절대 1 KB와 상대 5% 중 작은 값을 쓴다 — 일률 1 KB는 4 KB짜리 라우트에
 * 25%여서 사실상 게이트가 아니었다. 다만 측정 해상도(0.1 KB) 근처까지 좁히면 코드와
 * 무관한 실패가 생겨 게이트를 신뢰하지 않게 되므로 0.3 KB를 바닥으로 둔다.
 */
export function toleranceKB(baselineKB) {
  return Math.max(0.3, Math.min(1, baselineKB * 0.05));
}

/**
 * 두 개 이상의 라우트가 함께 받는 청크 = 공용 셸. manifest의 `/layout` 키로 나누는
 * 방법은 믿을 수 없다 — `(ops)` 셸처럼 layout 키에 안 잡히는 청크가 있어 공용이
 * 라우트 전용으로 잘못 분류된다. 전 라우트를 측정한 뒤 등장 횟수를 세는 쪽이 정확하다.
 */
export function sharedChunkSet(routeChunks) {
  const seen = new Map();
  for (const chunks of Object.values(routeChunks)) {
    for (const chunk of new Set(chunks)) {
      seen.set(chunk, (seen.get(chunk) ?? 0) + 1);
    }
  }
  return new Set([...seen].filter(([, count]) => count > 1).map(([chunk]) => chunk));
}

/**
 * 한 라우트의 청크를 공용/전용으로 갈라 각각의 크기를 낸다. `sizeOfBytes`는 **바이트**를
 * 돌려줘야 한다 — 청크마다 KB로 반올림한 뒤 더하면 합계가 원래 측정과 0.1 KB씩 어긋난다.
 */
export function splitSizes(chunks, sharedChunks, sizeOfBytes) {
  let shared = 0;
  let route = 0;
  for (const chunk of new Set(chunks)) {
    if (sharedChunks.has(chunk)) shared += sizeOfBytes(chunk);
    else route += sizeOfBytes(chunk);
  }
  return { sharedKB: toKB(shared), routeKB: toKB(route) };
}

export function toKB(bytes) {
  return round1(bytes / 1024);
}

export function round1(value) {
  return Math.round(value * 10) / 10;
}

/**
 * 라우트 하나의 예산 판정. 목표(maxKB) 달성 여부와 회귀(currentKB) 여부를 **항상 둘 다**
 * 계산한다 — KF-04에서 목표 이내면 회귀 비교에 도달조차 못 했던 버그를 되풀이하지 않는다.
 */
export function evaluateRoute({ maxKB, currentKB, currentSharedKB, currentRouteKB }, actual) {
  const withinTarget = actual.totalKB <= maxKB;

  if (currentKB === undefined) {
    return withinTarget
      ? { status: "통과", failed: false, withinTarget, noBaseline: true }
      : { status: "기준선 없음", failed: true, withinTarget, noBaseline: true };
  }

  const allowedKB = round1(toleranceKB(currentKB));
  const grew = actual.totalKB > currentKB + allowedKB;
  return {
    status: grew ? "회귀" : withinTarget ? "통과" : "유지",
    failed: grew,
    withinTarget,
    allowedKB,
    // 회귀의 원인이 공용 셸인지 그 화면인지 — 기준선이 있을 때만 귀속할 수 있다.
    sharedDeltaKB:
      currentSharedKB === undefined ? undefined : round1(actual.sharedKB - currentSharedKB),
    routeDeltaKB:
      currentRouteKB === undefined ? undefined : round1(actual.routeKB - currentRouteKB),
  };
}
