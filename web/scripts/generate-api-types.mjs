// 백엔드 OpenAPI 그룹 문서에서 TypeScript 타입을 생성한다.
//
// FE-API-02: 계약이 두 그룹으로 나뉘어 있다(OpenApiConfig의 GroupedOpenApi).
//   /v3/api-docs/public → src/generated/api-types.ts     (공개 API)
//   /v3/api-docs/ops    → src/generated/ops-api-types.ts (운영 콘솔, prod에서는 미노출)
// 한 파일로 합치지 않는 이유는 공개 계약 파일이 ops 스키마로 오염되지 않게 하기 위해서다.
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
const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://localhost:8080";

/** 그룹 하나 = 생성 파일 하나. 백엔드 OpenApiConfig의 GroupedOpenApi group 이름과 맞춘다. */
export const GROUPS = [
  { group: "public", file: "api-types.ts" },
  { group: "ops", file: "ops-api-types.ts" },
];

const specUrlOf = (group) => `${backendUrl}/v3/api-docs/${group}`;
const outputPathOf = (file) => path.join(webDir, "src", "generated", file);

function header(specUrl) {
  return [
    "// 자동 생성 파일 — 손으로 고치지 마세요.",
    `// 원본: ${specUrl}`,
    "// 재생성: npm run generate:api-types (백엔드가 로컬에 떠 있어야 함)",
    "",
    "",
  ].join("\n");
}

async function generate(group) {
  const specUrl = specUrlOf(group);
  try {
    return header(specUrl) + astToString(await openapiTS(new URL(specUrl)));
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
  // 환경 실패(백엔드 미기동)는 첫 그룹에서 즉시 던진다 — 나머지를 시도해도 같은 결과다.
  const results = [];
  for (const { group, file } of GROUPS) {
    results.push({ group, file, generated: await generate(group) });
  }

  if (!verify) {
    for (const { file, generated } of results) {
      const outputPath = outputPathOf(file);
      mkdirSync(path.dirname(outputPath), { recursive: true });
      writeFileSync(outputPath, generated);
      console.log(`생성 완료: ${outputPath}`);
    }
    return;
  }

  // 드리프트는 그룹마다 따로 보고한다 — 한 번의 CI 실행으로 두 계약을 모두 본다.
  const drifted = [];
  for (const { file, generated } of results) {
    const outputPath = outputPathOf(file);
    if (!existsSync(outputPath)) {
      drifted.push(`src/generated/${file} (파일 없음)`);
    } else if (!matchesCommittedContract(generated, readFileSync(outputPath, "utf8"))) {
      drifted.push(`src/generated/${file}`);
    }
  }

  if (drifted.length > 0) {
    throw new ApiTypeGenerationError(
      "CONTRACT_DRIFT",
      `${drifted.join(", ")}이(가) 현재 백엔드 계약과 다릅니다. "npm run generate:api-types" 후 커밋하세요.`,
    );
  }

  console.log(`OK: 생성 타입 ${GROUPS.length}개 그룹이 모두 현재 백엔드 계약과 일치합니다.`);
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
