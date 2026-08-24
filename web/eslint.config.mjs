import nextConfig from "eslint-config-next";

//  — 4계층 단방향 의존을 도구로 강제한다.
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

// FE-SEC-03(docs/improvement.md): serverEnv/publicEnv 경계를 관례가 아니라 도구로 강제한다.
//
// "use client" 파일이 shared/config/env의 serverEnv를 import하면 에러 처리한다 — 서버 전용
// 비밀(revalidateSecret 등)이 클라이언트 컴포넌트에서 참조되면 빌드는 통과하지만 webpack
// DefinePlugin이 비-NEXT_PUBLIC_ 변수를 undefined로 스텁 처리해 런타임에 조용히 잘못
// 동작한다. no-restricted-imports(위 forbidLayers)는 파일 **경로** glob 기준이라 "use client"
// 지시어처럼 파일 **내용** 기준 조건에는 못 쓴다 — 그래서 여기서는 작은 커스텀 규칙을 쓴다.
//
// entities/*/api.ts(예: community-comment/api.ts)는 서버 전용 함수(serverFetch)와 클라이언트
// 전용 함수(browserQuery/browserMutate)를 의도적으로 한 파일에 공존시킨다 — 그 파일 자체는
// "use client"가 아니므로 이 규칙 대상이 아니다. server-only 패키지로 모듈 단위 분리를
// 시도하면 이 공존 패턴을 쓰는 "use client" 소비자(comment-section.tsx 등)의 빌드가 깨진다
// (개별 export 사용 여부와 무관하게 모듈 전체가 막힌다) — 그래서 그 경로 대신 이 규칙을 쓴다.
const noServerEnvInClientPlugin = {
  rules: {
    "no-server-env-in-client": {
      meta: {
        type: "problem",
        docs: {
          description:
            '"use client" 파일에서 shared/config/env의 serverEnv를 import하지 못하게 막습니다.',
        },
        schema: [],
      },
      create(context) {
        let isClientModule = false;
        return {
          Program(node) {
            const first = node.body[0];
            isClientModule =
              first?.type === "ExpressionStatement" &&
              first.expression.type === "Literal" &&
              first.expression.value === "use client";
          },
          ImportDeclaration(node) {
            if (!isClientModule) return;
            if (!/(^|\/)shared\/config\/env$/.test(node.source.value)) return;
            for (const specifier of node.specifiers) {
              if (specifier.type === "ImportSpecifier" && specifier.imported.name === "serverEnv") {
                context.report({
                  node: specifier,
                  message:
                    '"use client" 파일에서 serverEnv를 import할 수 없습니다(FE-SEC-03) — 서버 전용 값이라 클라이언트 번들에서 undefined가 됩니다. publicEnv만 쓰세요.',
                });
              }
            }
          },
        };
      },
    },
  },
};

const envBoundaryRule = {
  files: ["src/**/*.{ts,tsx}"],
  plugins: { "kraft-env-boundary": noServerEnvInClientPlugin },
  rules: { "kraft-env-boundary/no-server-env-in-client": "error" },
};

const config = [
  { ignores: [".next/**", ".next-*/**", "next-env.d.ts"] },
  ...nextConfig,
  ...layerRules,
  envBoundaryRule,
];

export default config;
