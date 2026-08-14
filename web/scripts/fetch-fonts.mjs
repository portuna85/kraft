// 자체 호스팅 폰트를 생성하는 1회성 vendor 스크립트 P-5.
//
// web-legacy/scripts/fetch-fonts.mjs를 이 앱 전용으로 들여왔다(레거시는 동결이라
// 그대로 두고 손대지 않는다). 다른 점은 한글 서브셋 하나뿐이다: 레거시는 완성형
// 음절 전부(U+AC00–D7A3, 11,172자)를 담지만, 이 앱은 커뮤니티 글·댓글처럼 사용자가
// 쓴 한글도 실려야 해서 "아무 글자나 와도 되는" 안전한 표준 서브셋이 필요했다.
// KS X 1001(현대 한글 2,350자, 옛 EUC-KR 완성형이 담던 바로 그 집합)을 골랐다 —
// 실사용 한글 문서의 사실상 전부를 커버하면서 11,172자보다 훨씬 작다.
//
// 이 2,350자 표를 손으로 나열하면 옮겨 적다 틀리기 쉽고 검증도 어렵다. 대신 Node가
// 기본 내장한 ICU의 EUC-KR 디코더로 KS X 1001 완성형 영역(리드바이트 0xB0–0xC8,
// 트레일바이트 0xA1–0xFE)을 전부 디코드해 얻는다 — 이 영역 자체가 표준의 정의이므로
// "실행 시점 계산"이 아니라 표준 문서를 코드로 읽는 것에 가깝다. 아래에서 만든
// 코드포인트 개수가 2,350이 아니면 스크립트가 즉시 실패한다(조용한 오서브셋 방지).
//
// 재실행 방법: `npm run fetch:fonts` (web/ 디렉터리에서, devDependency `subset-font` 필요)
// 폰트를 갱신하려면 아래 GOOGLE_FONTS_COMMIT을 최신 커밋 SHA로 바꾼 뒤 재실행한다.
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import subsetFont from "subset-font";

const GOOGLE_FONTS_COMMIT = "684b69db51d59a3137ec0152fa3a3afc6f1b3814";
const RAW_BASE = `https://raw.githubusercontent.com/google/fonts/${GOOGLE_FONTS_COMMIT}/ofl`;

const webDir = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const outDir = path.join(webDir, "public", "fonts");

const KS_X_1001_HANGUL_SYLLABLE_COUNT = 2350;

function ksX1001HangulSyllables() {
  const decoder = new TextDecoder("euc-kr", { fatal: false });
  const syllables = new Set();
  for (let lead = 0xb0; lead <= 0xc8; lead++) {
    for (let trail = 0xa1; trail <= 0xfe; trail++) {
      const decoded = decoder.decode(Buffer.from([lead, trail]));
      if (decoded.length !== 1) continue;
      const codePoint = decoded.codePointAt(0);
      if (codePoint >= 0xac00 && codePoint <= 0xd7a3) syllables.add(codePoint);
    }
  }
  if (syllables.size !== KS_X_1001_HANGUL_SYLLABLE_COUNT) {
    throw new Error(
      `KS X 1001 음절 개수가 ${KS_X_1001_HANGUL_SYLLABLE_COUNT}이어야 하는데 ${syllables.size}개 나왔습니다 — ` +
        "Node ICU 빌드가 EUC-KR을 지원하지 않을 수 있습니다(full-icu 확인).",
    );
  }
  return [...syllables].map((codePoint) => String.fromCodePoint(codePoint)).join("");
}

const HANGUL_SYLLABLES = ksX1001HangulSyllables();
// 한글 자모 U+1100–11FF, 호환 자모 U+3130–318F
const HANGUL_JAMO = range(0x1100, 0x11ff) + range(0x3130, 0x318f);
// CJK 기호/구두점(원화 등 일부 포함 안 됨) U+3000–303F, 일반 구두점 U+2000–206F,
// 통화 기호(₩) U+20A0–20CF, 기본 라틴 U+0020–007E, 라틴-1 보충 U+00A0–00FF
const PUNCTUATION_AND_LATIN =
  range(0x3000, 0x303f) +
  range(0x2000, 0x206f) +
  range(0x20a0, 0x20cf) +
  range(0x0020, 0x007e) +
  range(0x00a0, 0x00ff);

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
    // og-font.ts가 next/og ImageResponse에서 굵게(700) 쓰는데, 그 렌더러(satori/resvg)는
    // woff2를 지원하지 않는다 — 이 weight만 추가로 woff도 함께 만든다.
    extraFormats: { 700: ["woff"] },
  },
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
