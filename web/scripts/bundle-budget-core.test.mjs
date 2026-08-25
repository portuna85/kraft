import { describe, expect, it } from "vitest";

import {
  entryPathOf,
  evaluateRoute,
  ownEntryKey,
  routeOf,
  sharedChunkSet,
  splitSizes,
  toleranceKB,
} from "./bundle-budget-core.mjs";

describe("routeOf / entryPathOf", () => {
  it("라우트 그룹 세그먼트를 URL에서 제거한다", () => {
    expect(routeOf(entryPathOf("(public)/page_client-reference-manifest.js"))).toBe("/");
    expect(routeOf(entryPathOf("(ops)/ops/page_client-reference-manifest.js"))).toBe("/ops");
    expect(
      routeOf(entryPathOf("(session)/community/posts/[id]/page_client-reference-manifest.js")),
    ).toBe("/community/posts/[id]");
  });

  it("윈도우 경로 구분자도 처리한다", () => {
    expect(entryPathOf("(public)\\stats\\page_client-reference-manifest.js")).toBe(
      "(public)/stats/page",
    );
  });
});

describe("ownEntryKey", () => {
  it("자기 경로와 정확히 일치하는 키를 고른다", () => {
    const entries = {
      "[project]/src/app/layout": [],
      "[project]/src/app/(public)/stats/page": [],
    };
    expect(ownEntryKey("(public)/stats/page", entries)).toBe(
      "[project]/src/app/(public)/stats/page",
    );
  });

  it("_not-found는 Next가 not-found로 이름 붙인 entry를 찾는다", () => {
    // 실측 manifest 키 구성 — 이 라우트가 오랫동안 바닥값으로 측정된 원인이다.
    const entries = {
      "[project]/src/app/icon--metadata": [],
      "[project]/src/app/layout": [],
      "[project]/src/app/not-found": [],
      "[project]/src/app/global-error": [],
    };
    expect(ownEntryKey("_not-found/page", entries)).toBe("[project]/src/app/not-found");
  });

  it("특수 이름이라도 키가 실제로 없으면 채택하지 않는다", () => {
    expect(ownEntryKey("_not-found/page", { "[project]/src/app/layout": [] })).toBeNull();
  });

  it("page 키가 하나뿐이면 그것을 쓰고, 여럿이면 포기한다", () => {
    expect(ownEntryKey("x/page", { "[project]/src/app/a/page": [] })).toBe(
      "[project]/src/app/a/page",
    );
    expect(
      ownEntryKey("x/page", { "[project]/src/app/a/page": [], "[project]/src/app/b/page": [] }),
    ).toBeNull();
  });
});

describe("toleranceKB", () => {
  it("큰 라우트는 절대 1 KB로 묶인다", () => {
    expect(toleranceKB(43)).toBe(1);
    expect(toleranceKB(23.9)).toBe(1);
  });

  it("작은 라우트는 5%로 조인다 — 일률 1 KB는 사실상 무게이트였다", () => {
    expect(toleranceKB(8.8)).toBeCloseTo(0.44, 5);
  });

  it("측정 지터 아래로는 내려가지 않는다(0.3 KB 바닥)", () => {
    expect(toleranceKB(4)).toBe(0.3);
    expect(toleranceKB(0)).toBe(0.3);
  });
});

describe("sharedChunkSet / splitSizes", () => {
  const routeChunks = {
    "/": ["icon", "publicShell"],
    "/stats": ["icon", "publicShell"],
    "/ops": ["icon", "opsShell", "opsPage"],
  };

  it("두 개 이상 라우트에 등장하는 청크만 공용으로 본다", () => {
    expect(sharedChunkSet(routeChunks)).toEqual(new Set(["icon", "publicShell"]));
  });

  it("layout 키에 안 잡히는 셸도 등장 횟수로 올바르게 갈린다", () => {
    // /ops의 셸은 layout entry에 나타나지 않아 layout 기준 분류로는 전용으로 오분류됐다.
    const shared = sharedChunkSet(routeChunks);
    const kb = (n) => n * 1024;
    const sizes = { icon: kb(4.3), opsShell: kb(12), opsPage: kb(9) };
    expect(splitSizes(routeChunks["/ops"], shared, (c) => sizes[c])).toEqual({
      sharedKB: 4.3,
      routeKB: 21,
    });
  });

  it("중복 청크를 두 번 세지 않는다", () => {
    const shared = new Set(["a"]);
    expect(splitSizes(["a", "a", "b"], shared, () => 2048)).toEqual({ sharedKB: 2, routeKB: 2 });
  });
});

describe("evaluateRoute", () => {
  const actual = { totalKB: 43.4, sharedKB: 34.8, routeKB: 8.6 };

  it("허용치 이내 증가는 통과시킨다", () => {
    const verdict = evaluateRoute({ maxKB: 63, currentKB: 43 }, actual);
    expect(verdict.failed).toBe(false);
    expect(verdict.status).toBe("통과");
    expect(verdict.allowedKB).toBe(1);
  });

  it("같은 폭이라도 작은 라우트에서는 회귀로 잡는다", () => {
    const small = { totalKB: 9.3, sharedKB: 4.3, routeKB: 5 };
    const verdict = evaluateRoute({ maxKB: 51, currentKB: 8.8 }, small);
    expect(verdict.failed).toBe(true);
    expect(verdict.status).toBe("회귀");
    expect(verdict.allowedKB).toBe(0.4);
  });

  it("회귀 시 공용/전용 증가분을 귀속한다", () => {
    const verdict = evaluateRoute(
      { maxKB: 63, currentKB: 43, currentSharedKB: 33.3, currentRouteKB: 8.6 },
      { totalKB: 44.9, sharedKB: 36.3, routeKB: 8.6 },
    );
    expect(verdict.failed).toBe(true);
    expect(verdict.sharedDeltaKB).toBe(3);
    expect(verdict.routeDeltaKB).toBe(0);
  });

  it("목표를 넘었지만 기준선 이내면 유지로 본다 — 회귀만 막는 현 단계", () => {
    const verdict = evaluateRoute({ maxKB: 40, currentKB: 43.5 }, actual);
    expect(verdict.failed).toBe(false);
    expect(verdict.status).toBe("유지");
    expect(verdict.withinTarget).toBe(false);
  });

  it("기준선이 없고 목표도 넘으면 실패시킨다", () => {
    const verdict = evaluateRoute({ maxKB: 40 }, actual);
    expect(verdict.failed).toBe(true);
    expect(verdict.noBaseline).toBe(true);
  });
});
