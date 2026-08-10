// OG 이미지 전용 폰트 로더 — Node.js 런타임에서만 동작한다(fs 사용).
//
// next/og(satori)의 렌더러는 woff2를 지원하지 않는다 — 본문에서 쓰는 woff2와 별도로
// .woff 하나를 따로 둔다(§7.5 폰트 전략과 다른 파일). 700 weight 하나로 한글·라틴
// 글리프를 전부 커버한다.

import { readFile } from "node:fs/promises";
import { join } from "node:path";

export type OgFontConfig = {
  fonts: { name: string; data: ArrayBuffer; weight: 700; style: "normal" }[];
  fontFamily: string;
};

let cached: OgFontConfig["fonts"] | null = null;

export async function getOgFontConfig(): Promise<OgFontConfig> {
  if (cached !== null) return { fonts: cached, fontFamily: "Noto Sans KR, sans-serif" };

  try {
    const bold = await readFile(join(process.cwd(), "public", "fonts", "noto-sans-kr-700.woff"));
    cached = [
      { name: "Noto Sans KR", data: bold.buffer as ArrayBuffer, weight: 700, style: "normal" },
    ];
    return { fonts: cached, fontFamily: "Noto Sans KR, sans-serif" };
  } catch {
    // 폰트를 못 읽어도 이미지 생성 자체는 계속한다 — 시스템 폴백 폰트로 렌더된다.
    return { fonts: [], fontFamily: "sans-serif" };
  }
}
