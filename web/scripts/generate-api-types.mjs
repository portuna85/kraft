// 백엔드 /v3/api-docs에서 TypeScript 타입을 생성한다 — improvement_fe.md §8.4.
//
// 생성 타입은 **컴파일 타임 계약**이고, Valibot 스키마는 **런타임 경계**다. 둘의 정합성은
// 각 스키마 옆의 타입 레벨 테스트가 잡는다(T-29). 어느 한쪽만으로는 부족하다 — 생성
// 타입은 런타임에 아무것도 검사하지 않고, 스키마만 있으면 백엔드 계약 변경을 놓친다.
//
// 산출물은 4계층 어디에도 속하지 않는 빌드 아티팩트라 src/generated/에 둔다.
// 백엔드가 로컬에 떠 있어야 한다(local 프로파일, H2).
//
//   npm run generate:api-types   재생성
//   npm run verify:api-types     커밋된 파일과 비교(다르면 실패) — CI 게이트
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import openapiTS, { astToString } from "openapi-typescript";

/** 계약이 어긋남 — 생성 파일을 갱신하고 커밋하면 해결된다 */
export const EXIT_CONTRACT_DRIFT = 1;
/** 스펙을 가져오지 못함 — 백엔드가 안 떠 있는 등 환경 문제 */
export const EXIT_ENVIRONMENT_FAILURE = 2;

export class ApiTypeGenerationError extends Error {
  constructor(kind, message, options = {}) {
    super(message, options);
    this.name = "ApiTypeGenerationError";
    this.kind = kind;
    this.exitCode = kind === "ENVIRONMENT_FAILURE" ? EXIT_ENVIRONMENT_FAILURE : EXIT_CONTRACT_DRIFT;
  }
}

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const outputPath = path.join(webDir, "src", "generated", "api-types.ts");
const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://localhost:8080";
const specUrl = `${backendUrl}/v3/api-docs`;

function header() {
  return [
    "// 자동 생성 파일 — 손으로 고치지 마세요.",
    `// 원본: ${specUrl}`,
    "// 재생성: npm run generate:api-types (백엔드가 로컬에 떠 있어야 함)",
    "",
    "",
  ].join("\n");
}

async function generate() {
  try {
    return header() + astToString(await openapiTS(new URL(specUrl)));
  } catch (cause) {
    throw new ApiTypeGenerationError(
      "ENVIRONMENT_FAILURE",
      `${specUrl}에서 OpenAPI 스펙을 가져오지 못했습니다. 백엔드를 local 프로파일로 띄운 뒤 다시 시도하세요.`,
      { cause },
    );
  }
}

export function matchesCommittedContract(generated, committed) {
  return committed === generated;
}

async function main({ verify }) {
  const generated = await generate();

  if (!verify) {
    mkdirSync(path.dirname(outputPath), { recursive: true });
    writeFileSync(outputPath, generated);
    console.log(`생성 완료: ${outputPath}`);
    return;
  }

  if (!existsSync(outputPath)) {
    throw new ApiTypeGenerationError(
      "CONTRACT_DRIFT",
      `${outputPath}가 없습니다. "npm run generate:api-types" 후 커밋하세요.`,
    );
  }

  if (!matchesCommittedContract(generated, readFileSync(outputPath, "utf8"))) {
    throw new ApiTypeGenerationError(
      "CONTRACT_DRIFT",
      'src/generated/api-types.ts가 현재 백엔드 계약과 다릅니다. "npm run generate:api-types" 후 커밋하세요.',
    );
  }

  console.log("OK: 생성 타입이 현재 백엔드 계약과 일치합니다.");
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    await main({ verify: process.argv.includes("--verify") });
  } catch (error) {
    if (!(error instanceof ApiTypeGenerationError)) throw error;
    console.error(`[${error.kind}] ${error.message}`);
    if (process.env.GITHUB_ACTIONS === "true") {
      console.error(`::error title=API type check ${error.kind}::${error.message}`);
    }
    if (error.cause) console.error(error.cause);
    process.exitCode = error.exitCode;
  }
}
