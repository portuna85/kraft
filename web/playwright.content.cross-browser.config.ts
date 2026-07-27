import { defineConfig, devices } from "@playwright/test";
import baseConfig from "./playwright.content.config";

export default defineConfig({
  ...baseConfig,
  projects: [
    { name: "Chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "Firefox", use: { ...devices["Desktop Firefox"] } },
    { name: "WebKit", use: { ...devices["Desktop Safari"] } },
  ],
});
