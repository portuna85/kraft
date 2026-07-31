import type { Metadata } from "next";
import { getPublicIncidents, getRoundFreshness } from "@/lib/api";
import { PageHeader } from "@/components/page-header";
import { StatusBadge } from "@/ui/primitives/status-badge";
import { formatDateTime, formatDrawDate } from "@/lib/format";
import logger from "@/lib/logger";
import styles from "./status.module.css";

export const metadata: Metadata = {
  title: "서비스 상태",
  description: "데이터 최신성과 최근 수집·보정 이력을 안내합니다.",
  robots: { index: false, follow: false },
  alternates: { canonical: "/status" },
};

export default async function StatusPage() {
  const [freshness, incidents] = await Promise.all([
    getRoundFreshness().catch((error) => {
      logger.warn({ err: error }, "데이터 최신성 조회 실패");
      return null;
    }),
    getPublicIncidents().catch((error) => {
      logger.warn({ err: error }, "공개 이력 조회 실패");
      return null;
    }),
  ]);

  return (
    <section className="panel">
      <PageHeader eyebrow="서비스 상태" title="서비스 상태" />

      <h2 className="section-title">현재 데이터 상태</h2>
      {freshness ? (
        <div className={styles.currentStatus}>
          <StatusBadge status={freshness.fresh ? "fresh" : "stale"} label={freshness.fresh ? "정상" : "반영 지연"} />
          <p className="muted">{freshness.latestRound}회 ({formatDrawDate(freshness.latestDrawDate)}) 까지 반영됨</p>
        </div>
      ) : (
        <p className="muted">상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
      )}

      <h2 className="section-title">최근 30일 수집·보정 이력</h2>
      {!incidents ? (
        <p className="muted">이력을 불러오지 못했습니다.</p>
      ) : incidents.length === 0 ? (
        <p className="muted">최근 30일 동안 기록된 수집 지연이나 보정 이력이 없습니다.</p>
      ) : (
        <ul className={styles.list} data-testid="status-incident-list">
          {incidents.map((incident) => (
            <li key={`${incident.type}-${incident.round}`} className={styles.item}>
              <span className={styles.round}>{incident.round ? `${incident.round}회` : "-"}</span>
              <span>
                {incident.type}
                {incident.occurrences > 1 ? ` (시도 ${incident.occurrences}회)` : ""}
              </span>
              <StatusBadge status={incident.resolved ? "fresh" : "stale"} label={incident.resolved ? "해결됨" : "확인 중"} />
              <span className={`${styles.time} muted`}>{formatDateTime(incident.occurredAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
