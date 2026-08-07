import type { ReactNode } from "react";

import "./globals.css";

// Phase 0 스캐폴드. 실제 셸(폰트·테마 부트스트랩·skip-nav·라우트 그룹 3분할)은
// Phase 2에서 만든다 — improvement_fe.md §10.1.
export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
