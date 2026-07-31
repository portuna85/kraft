import type { Metadata } from "next";
import { OpsDashboardClient } from "@/components/ops-dashboard-client";
import { PageHeader } from "@/components/page-header";

export const metadata: Metadata = {
  title: "운영 대시보드",
  description: "운영 토큰으로 회차 상태를 점검하고 수집 및 수동 적재 작업을 수행합니다.",
  robots: {
    index: false,
    follow: false
  }
};

export default function OpsPage() {
  return (
    <section className="grid">
      <div className="panel">
        <PageHeader eyebrow="내부 운영" title="회차 운영 대시보드" description={<>
          이 화면은 운영 전용입니다. 공개 도메인에서는 차단되며, 실제 운영 API 호출은
          <code> /ops-api/* </code>
          프록시 경로로만 전달됩니다.
        </>} />
      </div>
      <OpsDashboardClient />
    </section>
  );
}
