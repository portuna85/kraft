import type { ReactNode } from "react";

import styles from "../_shell/shell.module.css";

/**
 * 운영 셸 — improvement_fe.md §10.1
 *
 * 공개 내비가 없다. 운영 화면에서 공개 사이트로 나가는 링크를 노출하면 호스트 게이트
 * 뒤에 있다는 사실이 흐려지고, 운영자가 실수로 공개 도메인 컨텍스트로 이동하게 된다.
 * 접근 통제 자체는 proxy의 호스트 게이트가 담당한다(§20.9).
 */
export default function OpsLayout({ children }: { children: ReactNode }) {
  return (
    <main id="main" className={`shell ${styles.main}`}>
      {children}
    </main>
  );
}
