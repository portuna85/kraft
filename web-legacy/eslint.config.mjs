import nextConfig from "eslint-config-next";

// KF-08: app → features → ui/lib 단방향을 강제한다. components/와 features/는 서로
// 같은 층위(둘 다 app이 쓰는 대상)라 서로를 참조하면 안 되고, 둘 다 app/**(라우트가
// 소유한 파일)을 참조해서도 안 된다 — 라우트 내부가 사실상 컴포넌트 API가 되는 걸
// 막기 위함이다. ui/는 최하위 계층이라 app/components/features 어느 것도 참조할 수
// 없다(lib은 예외 — ui와 lib은 같은 기반 계층으로 취급).
const layerBoundaryRules = [
  {
    files: ["src/components/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/features/*", "@/features", "@/app/*"],
              message:
                "components/**는 features/**나 app/**를 참조할 수 없다(KF-08) — ui/lib만 사용하거나, features가 필요하면 그 컴포넌트를 features/로 옮기세요.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/features/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/components/*", "@/components", "@/app/*"],
              message:
                "features/**는 components/**나 app/**를 참조할 수 없다(KF-08) — ui/lib만 사용하세요.",
            },
          ],
        },
      ],
    },
  },
  {
    files: ["src/ui/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@/app/*", "@/components/*", "@/components", "@/features/*", "@/features"],
              message: "ui/**는 최하위 계층이라 app/components/features를 참조할 수 없다(KF-08) — lib만 사용하세요.",
            },
          ],
        },
      ],
    },
  },
];

const config = [...nextConfig, ...layerBoundaryRules];

export default config;
