import nextConfig from "eslint-config-next";

// improvement_fe.md §7.3 — 4계층 단방향 의존을 도구로 강제한다.
//
//   app      → features, entities, shared   ✅
//   features → entities, shared             ✅
//   entities → shared                       ✅
//   shared   → shared                       ✅ (그 외 전부 ❌)
//
// 규칙이 문서에만 있으면 위반이 조용히 누적된다는 것이 현행 구조가 무너진 원인이므로
// (§6.2 H-1), 코드보다 이 규칙이 먼저다. features 간 직접 의존이 필요해지면 그것은
// "entities로 올라가야 할 개념"이라는 신호다.
//
// 패턴은 import 문자열에 minimatch로 매칭되므로 `@/features/x`(별칭)와
// `../../features/x`(상대 경로 우회)를 한 번에 막는다 — 별칭만 막으면 상대 경로로
// 그대로 새어 나간다.
function forbidLayers(files, forbidden, message) {
  return {
    files,
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: forbidden.flatMap((layer) => [
                `@/${layer}`,
                `@/${layer}/**`,
                `**/${layer}/**`,
              ]),
              message,
            },
          ],
        },
      ],
    },
  };
}

const layerRules = [
  forbidLayers(
    ["src/shared/**/*.{ts,tsx}"],
    ["app", "features", "entities"],
    "shared는 상위 계층을 참조할 수 없습니다 — shared에는 도메인(로또·게시글·회차)을 모르는 코드만 둡니다.",
  ),
  forbidLayers(
    ["src/entities/**/*.{ts,tsx}"],
    ["app", "features", "entities"],
    "entities는 app·features와 다른 entity를 참조할 수 없습니다 — shared만 쓰고, 같은 entity 내부는 상대 경로로 import 하세요.",
  ),
  forbidLayers(
    ["src/features/**/*.{ts,tsx}"],
    ["app", "features"],
    "features는 app과 다른 feature를 참조할 수 없습니다 — 두 feature가 같은 것을 필요로 하면 그것은 entities로 올라가야 할 개념입니다. 같은 feature 내부는 상대 경로로 import 하세요.",
  ),
];

const config = [
  { ignores: [".next/**", ".next-*/**", "next-env.d.ts"] },
  ...nextConfig,
  ...layerRules,
];

export default config;
