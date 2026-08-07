// F-03: 백엔드 /v3/api-docs(B-01)에서 TypeScript 타입을 생성한다. 백엔드가 로컬에서 떠
// 있어야 한다(예: ./gradlew.bat bootRun, local 프로파일 — Docker 불필요, H2 사용).
//
// 재생성:  npm run generate:api-types
// 드리프트 검증(CI/로컬): npm run verify:api-types — 재생성 결과를 커밋된 파일과 비교해
// 다르면 실패한다(계약이 바뀌었는데 생성 파일을 갱신하지 않은 경우를 잡는다).
import { writeFileSync, readFileSync, mkdirSync, existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import openapiTS, { astToString } from "openapi-typescript";

export const EXIT_CONTRACT_DRIFT = 1;
export const EXIT_ENVIRONMENT_FAILURE = 2;

export class ApiTypeGenerationError extends Error {
  constructor(kind, message, options = {}) {
    super(message, options);
    this.name = "ApiTypeGenerationError";
    this.kind = kind;
    this.exitCode =
      kind === "ENVIRONMENT_FAILURE" ? EXIT_ENVIRONMENT_FAILURE : EXIT_CONTRACT_DRIFT;
  }
}

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const outputPath = path.join(webDir, "src", "lib", "generated", "api-types.ts");
const backendUrl = process.env.KRAFT_BACKEND_INTERNAL_URL ?? "http://localhost:8080";
const specUrl = `${backendUrl}/v3/api-docs`;
const isVerify = process.argv.includes("--verify");

const HEADER =
  "// AUTO-GENERATED — do not edit by hand.\n" +
  `// Source: ${specUrl}\n` +
  "// Regenerate: npm run generate:api-types (backend must be running locally)\n\n";

async function generate() {
  let ast;
  try {
    ast = await openapiTS(new URL(specUrl));
  } catch (cause) {
    throw new ApiTypeGenerationError(
      "ENVIRONMENT_FAILURE",
      `Could not fetch the OpenAPI spec from ${specUrl}. Start the backend with the local profile and retry.`,
      { cause },
    );
  }
  return HEADER + astToString(ast);
}

export function verifyGeneratedContract(generated, committed) {
  return committed === generated;
}

async function main() {
  const generated = await generate();

  if (!isVerify) {
    mkdirSync(path.dirname(outputPath), { recursive: true });
    writeFileSync(outputPath, generated);
    console.log(`Wrote ${outputPath}`);
    return;
  }

  if (!existsSync(outputPath)) {
    throw new ApiTypeGenerationError(
      "CONTRACT_DRIFT",
      `${outputPath} does not exist. Run "npm run generate:api-types" and commit it.`,
    );
  }
  const committed = readFileSync(outputPath, "utf8");
  if (!verifyGeneratedContract(generated, committed)) {
    throw new ApiTypeGenerationError(
      "CONTRACT_DRIFT",
      "generated/api-types.ts is out of date with the backend OpenAPI contract. " +
        "Run \"npm run generate:api-types\" and commit the result.",
    );
  }
  console.log("OK: generated/api-types.ts matches the current backend contract.");
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    await main();
  } catch (error) {
    if (error instanceof ApiTypeGenerationError) {
      console.error(`[${error.kind}] ${error.message}`);
      if (process.env.GITHUB_ACTIONS === "true") {
        console.error(`::error title=API type check ${error.kind}::${error.message}`);
      }
      if (error.cause) console.error(error.cause);
      process.exitCode = error.exitCode;
    } else {
      throw error;
    }
  }
}
