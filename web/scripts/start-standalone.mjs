// 프로덕션과 동일한 standalone 산출물로 앱을 띄운다 — `npm start`와 E2E가 같은 절차를
// 쓴다(Dockerfile도 동일). `next start`는 output:"standalone" 산출물과 호환되지 않으므로
// static·public을 standalone 아래로 복사한 뒤 server.js를 직접 실행한다.
//
// NEXT_DIST_DIR을 주면 next.config.ts와 같은 distDir을 쓴다 — 별도 빌드가 필요한 e2e
// 트랙이 기본 산출물을 덮어쓰지 않게 하기 위함이다.
import { spawn } from "node:child_process";
import { cpSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const distDirName = process.env.NEXT_DIST_DIR ?? ".next";
const distDir = path.join(webDir, distDirName);
const standaloneDir = path.join(distDir, "standalone");

if (!existsSync(standaloneDir)) {
  console.error(
    `ERROR: ${distDirName}/standalone이 없습니다. 같은 NEXT_DIST_DIR로 "npm run build"를 먼저 실행하세요.`,
  );
  process.exit(1);
}

cpSync(path.join(distDir, "static"), path.join(standaloneDir, distDirName, "static"), {
  recursive: true,
});
cpSync(path.join(webDir, "public"), path.join(standaloneDir, "public"), { recursive: true });

const child = spawn(process.execPath, [path.join(standaloneDir, "server.js")], {
  stdio: "inherit",
  env: {
    ...process.env,
    PORT: process.env.PORT ?? "3000",
    // Docker는 HOSTNAME=<container-id>를 주입하고, standalone 서버는 그 호스트명에만
    // 바인딩한다 — Playwright와 헬스체크는 127.0.0.1로 접근하므로 그대로 두면 붙지 못한다.
    HOSTNAME: process.env.KRAFT_BIND_HOST ?? "0.0.0.0",
  },
});

child.on("exit", (code) => process.exit(code ?? 0));
