// F1: 자체 호스팅 폰트를 생성하는 1회성 vendor 스크립트.
//
// next/font/google은 빌드마다 fonts.gstatic.com에서 폰트를 받아오므로 네트워크 장애가
// production build의 단일 실패 지점이 된다. 이 스크립트는 google/fonts 공식 저장소
// (OFL 라이선스)에서 variable font를 받아 필요한 weight만 고정(pin)한 뒤 woff2로 export해
// web/public/fonts에 커밋한다. next/font/local이 이 결과물을 참조하므로 실제 빌드에는
// 네트워크가 필요 없다.
//
// 재실행 방법: `node scripts/fetch-fonts.mjs` (web/ 디렉터리에서 실행, devDependency로
// 설치된 subset-font 필요: `npm install`)
//
// 폰트를 갱신하려면 아래 GOOGLE_FONTS_COMMIT을 최신 커밋 SHA로 바꾼 뒤 재실행한다.
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import subsetFont from "subset-font";

const GOOGLE_FONTS_COMMIT = "684b69db51d59a3137ec0152fa3a3afc6f1b3814";
const RAW_BASE = `https://raw.githubusercontent.com/google/fonts/${GOOGLE_FONTS_COMMIT}/ofl`;

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const outDir = path.join(webDir, "public", "fonts");

// 한글 완성형 음절 U+AC00–D7A3
const HANGUL_SYLLABLES = range(0xac00, 0xd7a3);
// 한글 자모 U+1100–11FF, 호환 자모 U+3130–318F
const HANGUL_JAMO = range(0x1100, 0x11ff) + range(0x3130, 0x318f);
// CJK 기호/구두점(원화 등 일부 포함 안 됨) U+3000–303F, 일반 구두점 U+2000–206F,
// 통화 기호(₩) U+20A0–20CF, 기본 라틴 U+0020–007E, 라틴-1 보충 U+00A0–00FF
const PUNCTUATION_AND_LATIN =
  range(0x3000, 0x303f) + range(0x2000, 0x206f) + range(0x20a0, 0x20cf) +
  range(0x0020, 0x007e) + range(0x00a0, 0x00ff);

const KOREAN_CHARSET = HANGUL_SYLLABLES + HANGUL_JAMO + PUNCTUATION_AND_LATIN;
const LATIN_CHARSET = range(0x0020, 0x007e) + range(0x00a0, 0x00ff) + range(0x2000, 0x206f);

function range(start, end) {
  let s = "";
  for (let cp = start; cp <= end; cp++) s += String.fromCodePoint(cp);
  return s;
}

const FONTS = [
  {
    family: "notosanskr",
    ttfName: "NotoSansKR[wght].ttf",
    outPrefix: "noto-sans-kr",
    weights: [400, 700],
    charset: KOREAN_CHARSET,
    // og-font-node.ts가 next/og ImageResponse에서 굵게(700) 쓰는데, 그 렌더러(satori/resvg)는
    // woff2를 지원하지 않는다 — 이 weight만 추가로 woff도 함께 만든다.
    extraFormats: { 700: ["woff"] },
  },
  // H-3: noto-serif-kr는 2026-08 기준 어떤 CSS도 참조하지 않아(--font-display 미사용)
  // 삭제했다. 디스플레이 폰트가 다시 필요해지면 이 FONTS 항목을 되살리고
  // web/src/app/layout.tsx에 localFont() 등록을 다시 추가한다.
  {
    family: "spacegrotesk",
    ttfName: "SpaceGrotesk[wght].ttf",
    outPrefix: "space-grotesk",
    weights: [500, 700],
    charset: LATIN_CHARSET,
  },
];

async function fetchBuffer(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`GET ${url} → ${res.status}`);
  return Buffer.from(await res.arrayBuffer());
}

async function main() {
  await mkdir(outDir, { recursive: true });

  for (const font of FONTS) {
    console.log(`==> ${font.family}`);
    const ttfUrl = `${RAW_BASE}/${font.family}/${encodeURIComponent(font.ttfName)}`;
    const oflUrl = `${RAW_BASE}/${font.family}/OFL.txt`;

    const ttfBuffer = await fetchBuffer(ttfUrl);
    const oflText = (await fetchBuffer(oflUrl)).toString("utf8");
    await writeFile(path.join(outDir, `LICENSE-${font.outPrefix}.OFL.txt`), oflText);

    for (const weight of font.weights) {
      const woff2 = await subsetFont(ttfBuffer, font.charset, {
        targetFormat: "woff2",
        variationAxes: { wght: weight },
      });
      const outPath = path.join(outDir, `${font.outPrefix}-${weight}.woff2`);
      await writeFile(outPath, woff2);
      console.log(`    ${path.basename(outPath)} — ${(woff2.length / 1024).toFixed(0)} KB`);

      for (const format of font.extraFormats?.[weight] ?? []) {
        const extra = await subsetFont(ttfBuffer, font.charset, {
          targetFormat: format,
          variationAxes: { wght: weight },
        });
        const extraPath = path.join(outDir, `${font.outPrefix}-${weight}.${format}`);
        await writeFile(extraPath, extra);
        console.log(`    ${path.basename(extraPath)} — ${(extra.length / 1024).toFixed(0)} KB`);
      }
    }
  }

  console.log(`\nOK: fonts written to ${outDir}`);
  console.log(`google/fonts commit: ${GOOGLE_FONTS_COMMIT}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
