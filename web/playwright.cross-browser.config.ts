import { defineConfig, devices } from "@playwright/test";

/**
 * Keep cross-browser coverage focused on semantic accessibility and responsive
 * layout contracts. Full Chromium suites remain the fast PR regression gate.
 */
export default defineConfig({
  testDir: "./e2e",
  testMatch: ["accessibility.spec.ts", "responsive.spec.ts"],
  testIgnore: ["content/**", "ad-overlay/**", "visual/**"],
  fullyParallel: true,
  workers: process.env.CI ? 4 : undefined,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // KF-10: 실측 데이터 수집(§계측 후 샤드 재조정) — playwright.config.ts와 동일 이유.
  reporter: process.env.CI ? [["github"], ["json", { outputFile: "playwright-timing.json" }]] : "list",
  use: {
    baseURL: "http://127.0.0.1:3103",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "webkit", use: { ...devices["Desktop Safari"] } },
  ],
  webServer: {
    command: "npm run e2e:serve",
    url: "http://127.0.0.1:3103",
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
    env: {
      KRAFT_BACKEND_INTERNAL_URL: "http://127.0.0.1:59999",
      KRAFT_PUBLIC_BASE_URL: "http://127.0.0.1:3103",
      PORT: "3103",
      KRAFT_OPS_ALLOWED_HOST: "127.0.0.1",
    },
  },
});
