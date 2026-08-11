import type { Metadata } from "next";

import { OpsConsole } from "@/features/ops-console/ops-console";
import { PageHeader } from "@/shared/ui/page-header";

export const metadata: Metadata = {
  title: "운영 콘솔",
  robots: { index: false, follow: false },
};

/**
 * 운영 콘솔 라우트 — improvement_fe.md §23.14.
 *
 * 서버 컴포넌트는 조립만 한다 — 실제 데이터는 전부 운영 토큰 인증이 필요한
 * 클라이언트 상호작용이라 서버에서 미리 fetch할 게 없다(legacy도 같은 구조:
 * 얇은 서버 페이지 + 클라이언트 대시보드).
 */
export default function OpsPage() {
  return (
    <div className="stack">
      <PageHeader
        eyebrow="내부 운영"
        title="회차 운영 콘솔"
        description="이 화면은 운영자 전용입니다. 공개 도메인에서는 접근할 수 없습니다."
      />
      <OpsConsole />
    </div>
  );
}
