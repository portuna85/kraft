// Node.js runtime 전용 폰트 로더 (opengraph-image.tsx에서 사용).
// Edge runtime route에서는 절대 import 금지 → og-font.ts 사용.

import { readFile } from "fs/promises";
import { join } from "path";

type FontEntry = {
  name: string;
  data: ArrayBuffer;
  weight: 700;
  style: "normal";
};

export type FontConfig = {
  fonts: FontEntry[];
  fontFamily: string;
};

let nodeCache: FontEntry[] | null = null;

export async function getOgFontConfig(): Promise<FontConfig> {
  if (nodeCache) return { fonts: nodeCache, fontFamily: "Noto Sans KR, sans-serif" };
  try {
    const base = join(process.cwd(), "public", "fonts");
    // scripts/fetch-fonts.mjs가 만든 700 weight 파일 하나에 한글·라틴 글리프가 모두 포함돼
    // 있어(F1) 예전처럼 korean/latin 두 파일을 따로 합칠 필요가 없다. next/og의 렌더러
    // (satori/resvg)가 woff2를 지원하지 않아 이 용도에는 .woff를 쓴다.
    const bold = await readFile(join(base, "noto-sans-kr-700.woff"));
    nodeCache = [
      { name: "Noto Sans KR", data: bold.buffer as ArrayBuffer, weight: 700, style: "normal" },
    ];
    return { fonts: nodeCache, fontFamily: "Noto Sans KR, sans-serif" };
  } catch {
    return { fonts: [], fontFamily: "sans-serif" };
  }
}
