#!/usr/bin/env node
// 사진 자산마다
// meta.json이 라이선스·출처·alt 원칙을 갖추고 있는지 검사한다. web/public/photos/에 아직
// 실제 자산이 없는 동안은 대상 디렉터리가 비어 있어 항상 통과한다 — 승인된 실제
// 사진이 추가되면 이 스크립트가 실질적으로 작동한다.
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const photosDir = path.join(repoRoot, "web", "public", "photos");

const REQUIRED_FIELDS = [
  "slug",
  "originalUrl",
  "creator",
  "license",
  "acquiredAt",
  "usageLocation",
  "aspectRatio",
  "focalPoint",
  "altPrinciple",
  "containsIdentifiablePerson",
  "exifStripped",
];

function checkAsset(dirName) {
  const errors = [];
  const assetDir = path.join(photosDir, dirName);
  const metaPath = path.join(assetDir, "meta.json");

  if (!existsSync(metaPath)) {
    return [`${dirName}: meta.json이 없습니다.`];
  }

  let meta;
  try {
    meta = JSON.parse(readFileSync(metaPath, "utf8"));
  } catch (cause) {
    return [`${dirName}: meta.json이 유효한 JSON이 아닙니다 (${cause.message}).`];
  }

  for (const field of REQUIRED_FIELDS) {
    if (meta[field] === undefined || meta[field] === null || meta[field] === "") {
      errors.push(`${dirName}: 필수 필드 "${field}"가 없습니다.`);
    }
  }

  if (meta.slug !== undefined && meta.slug !== dirName) {
    errors.push(`${dirName}: meta.json의 slug("${meta.slug}")가 디렉터리명과 다릅니다.`);
  }

  if (meta.containsIdentifiablePerson === true && !meta.modelReleaseUrl) {
    errors.push(`${dirName}: containsIdentifiablePerson=true인데 modelReleaseUrl이 없습니다.`);
  }

  return errors;
}

if (!existsSync(photosDir)) {
  console.log(`OK: ${photosDir} 없음 — 아직 사진 자산이 없습니다.`);
  process.exit(0);
}

const entries = readdirSync(photosDir).filter((name) => statSync(path.join(photosDir, name)).isDirectory());

if (entries.length === 0) {
  console.log("OK: 등록된 사진 자산이 없습니다.");
  process.exit(0);
}

const allErrors = entries.flatMap(checkAsset);

if (allErrors.length > 0) {
  console.error("사진 자산 메타데이터 검사 실패:");
  for (const error of allErrors) console.error(`  - ${error}`);
  process.exit(1);
}

console.log(`OK: 사진 자산 ${entries.length}개 모두 메타데이터 요건을 충족합니다.`);
