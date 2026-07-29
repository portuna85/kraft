import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

// Kept outside Vitest's *.test.* discovery; these exercise the standalone CLI.
import {
  EXIT_ENVIRONMENT_FAILURE,
  verifyGeneratedContract,
} from "./generate-api-types.mjs";

const scriptsDir = path.dirname(fileURLToPath(import.meta.url));

test("generated contract comparison detects drift", () => {
  assert.equal(verifyGeneratedContract("same", "same"), true);
  assert.equal(verifyGeneratedContract("new", "old"), false);
});

test("backend connectivity failure has a distinct exit code and category", () => {
  const result = spawnSync(process.execPath, [path.join(scriptsDir, "generate-api-types.mjs"), "--verify"], {
    cwd: path.dirname(scriptsDir),
    encoding: "utf8",
    env: {
      ...process.env,
      KRAFT_BACKEND_INTERNAL_URL: "http://127.0.0.1:1",
    },
    timeout: 10_000,
  });

  assert.equal(result.status, EXIT_ENVIRONMENT_FAILURE);
  assert.match(result.stderr, /\[ENVIRONMENT_FAILURE\]/);
  assert.doesNotMatch(result.stderr, /\[CONTRACT_DRIFT\]/);
});
