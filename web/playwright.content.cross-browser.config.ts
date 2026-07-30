import { defineConfig, devices } from "@playwright/test";
import baseConfig from "./playwright.content.config";

// RW-P1-07: 이 설정은 예전엔 어느 npm 스크립트에도 CI 잡에도 연결되지 않은 고아
// 설정이었다 — content 트랙(실콘텐츠·픽스처 백엔드 전제)이 Firefox/WebKit에서
// 한 번도 실행된 적이 없었다는 뜻이다. content/** 전체를 3배로 돌리면 KF-10이
// 지적한 CI 팽창을 그대로 재현하므로, 브라우저 렌더링 차이에 실제로 민감한
// 반응형/오버플로/시각 계약 스펙만 골라 최소 증분으로 연결한다(playwright.
// cross-browser.config.ts가 기본 트랙에서 쓰는 것과 같은 절제 패턴).
export default defineConfig({
  ...baseConfig,
  testMatch: [
    "community-responsive.spec.ts",
    "overflow.spec.ts",
    "text-zoom.spec.ts",
    "forced-colors.spec.ts",
  ],
  projects: [
    { name: "Firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "WebKit", use: { ...devices["Desktop Safari"] } },
  ],
});
