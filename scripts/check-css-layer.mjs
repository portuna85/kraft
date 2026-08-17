#!/usr/bin/env node
// F-07: web/src/app/globals.css가 `@layer reset, tokens, base, components, utilities;`를
// 선언하고 각 CSS Module이 `@layer components { ... }`로 자신을 감싸는 것을 전제로,
// utilities가 항상 components를 이긴다는 캐스케이드 계약을 세운다(web/src/shared/styles/
// utilities.css 상단 주석). 이 래핑을 빠뜨린 모듈은 unlayered author style이 되는데,
// CSS Cascade Layers 규칙상 unlayered는 모든 named layer(utilities 포함)를 이긴다 —
// 즉 그 파일의 스타일이 유틸리티보다도 우선하는, 계약을 뒤집는 결함이다. 실제로 4개
// 파일이 이 상태로 방치돼 있었다(page-header/stat/page-skeleton/ops-console.module.css).
// 이 스크립트는 재발을 막는다.
import { globSync, readFileSync } from "node:fs";

const REPO_ROOT = new URL("..", import.meta.url).pathname.replace(/^\/([A-Za-z]):/, "$1:");

const files = globSync("web/src/**/*.module.css", { cwd: REPO_ROOT });

const failures = [];

for (const relPath of files) {
  const absPath = `${REPO_ROOT}/${relPath}`;
  const source = readFileSync(absPath, "utf8");
  // 블록 주석을 전부 제거한 뒤 첫 비공백 줄을 본다 — 여러 파일이 여러 줄짜리
  // `/* ... */` 헤더 주석으로 시작해 순수 grep으로는 "첫 실질 내용"을 안정적으로
  // 가려낼 수 없다.
  const withoutComments = source.replace(/\/\*[\s\S]*?\*\//g, "");
  const firstContentLine = withoutComments
    .split("\n")
    .map((line) => line.trim())
    .find((line) => line.length > 0);

  if (firstContentLine !== "@layer components {") {
    failures.push(relPath);
  }
}

if (failures.length > 0) {
  console.error("ERROR: 다음 CSS Module이 @layer components로 감싸여 있지 않다 —");
  console.error("       utilities가 항상 components를 이긴다는 규칙이 import 순서에");
  console.error("       좌우된다(unlayered 스타일은 모든 layer를 이긴다):");
  for (const f of failures) console.error(`  - ${f}`);
  process.exit(1);
}

console.log(`OK: CSS Module ${files.length}개 전부 @layer components로 감싸여 있다`);
