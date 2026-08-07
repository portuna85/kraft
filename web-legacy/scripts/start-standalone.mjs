// F2: `npm start`와 E2E 모두 prod와 동일한 `output: standalone` 산출물로 띄운다
// (Dockerfile과 동일 절차). `next start`는 standalone 빌드와 호환되지 않으므로
// <distDir>/static, public을 <distDir>/standalone 아래로 복사한 뒤 standalone server.js를
// 직접 실행한다. E2E는 각 playwright 설정의 webServer.env에서 PORT를 명시한다.
// NEXT_DIST_DIR을 지정하면 next.config.ts와 동일한 distDir을 참조한다(광고
// 오버레이 전용 빌드처럼 다른 e2e 트랙의 .next/standalone과 산출물이 겹치지 않게 할 때만 사용
// — 미지정 시 기존 동작과 100% 동일).
import { cpSync, existsSync } from "node:fs";
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const distDirName = process.env.NEXT_DIST_DIR ?? ".next";
const standaloneDir = path.join(webDir, distDirName, "standalone");

if (!existsSync(standaloneDir)) {
  console.error(`ERROR: ${distDirName}/standalone not found. Run "npm run build" first (with the same NEXT_DIST_DIR if set).`);
  process.exit(1);
}

cpSync(path.join(webDir, distDirName, "static"), path.join(standaloneDir, distDirName, "static"), { recursive: true });
cpSync(path.join(webDir, "public"), path.join(standaloneDir, "public"), { recursive: true });

const child = spawn(process.execPath, [path.join(standaloneDir, "server.js")], {
  stdio: "inherit",
  env: {
    ...process.env,
    PORT: process.env.PORT ?? "3000",
    // Docker injects HOSTNAME=<container-id>. Next's standalone server then
    // binds only to that hostname, while Playwright/health checks use
    // 127.0.0.1. Bind all interfaces unless an operator explicitly overrides
    // the address through the project-owned variable.
    HOSTNAME: process.env.KRAFT_BIND_HOST ?? "0.0.0.0",
  },
});

child.on("exit", (code) => process.exit(code ?? 0));
