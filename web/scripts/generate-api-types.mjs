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
    console.error(`ERROR: failed to fetch OpenAPI spec from ${specUrl}.`);
    console.error("Is the backend running locally? (./gradlew.bat bootRun, local profile)");
    console.error(cause);
    process.exit(1);
  }
  return HEADER + astToString(ast);
}

const generated = await generate();

if (isVerify) {
  if (!existsSync(outputPath)) {
    console.error(`ERROR: ${outputPath} does not exist. Run "npm run generate:api-types" first.`);
    process.exit(1);
  }
  const committed = readFileSync(outputPath, "utf8");
  if (committed !== generated) {
    console.error(
      "ERROR: generated/api-types.ts is out of date with the backend OpenAPI contract.\n" +
        "Run \"npm run generate:api-types\" and commit the result."
    );
    process.exit(1);
  }
  console.log("OK: generated/api-types.ts matches the current backend contract.");
} else {
  mkdirSync(path.dirname(outputPath), { recursive: true });
  writeFileSync(outputPath, generated);
  console.log(`Wrote ${outputPath}`);
}
