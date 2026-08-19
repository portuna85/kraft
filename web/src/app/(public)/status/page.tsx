import type { Metadata } from "next";

import { getRoundFreshness } from "@/entities/round/api";
import { getStatusIncidents } from "@/entities/status-incident/api";
import { incidentTypeLabel } from "@/entities/status-incident/schema";
import { formatDateTime, formatDrawDate } from "@/shared/lib/format";
import { StatusBadge } from "@/shared/ui/badge";
import { EmptyState, InlineAlert } from "@/shared/ui/states";
import { Table } from "@/shared/ui/surface";

import styles from "./status.module.css";

export const metadata: Metadata = {
  title: "서비스 상태",
  description: "당첨번호 데이터가 어디까지 반영됐는지와 최근 30일 수집·보정 이력을 공개합니다.",
  alternates: { canonical: "/status" },
};

/**
 * 이 페이지는 **부분 실패를 허용한다**
 *
 * 다른 화면과 다르다. 여기서 5xx를 내면 "서비스가 어떤 상태인지 알려 주는 페이지"가
 * 정작 문제가 있을 때 같이 죽는다. 두 데이터는 서로 독립이므로 각각 폴백한다.
 */
export default async function StatusPage() {
  const [freshness, incidents] = await Promise.all([
    getRoundFreshness().catch(() => null),
    getStatusIncidents().catch(() => null),
  ]);

  return (
    <div className="stack">
      <header className="prose stack">
        <h1>서비스 상태</h1>
        <p>
          당첨번호 데이터가 어디까지 반영됐는지와, 최근 30일 사이에 있었던 수집·보정 이력을
          공개합니다.
        </p>
      </header>

      <section aria-labelledby="freshness" className="stack">
        <h2 id="freshness">현재 데이터 상태</h2>
        {freshness === null ? (
          // KF-23(docs/improvement.md): 평문 <p>라 실패인데도 스크린리더에
          // 무성이었다 — role="alert"를 주는 InlineAlert로 바꾼다.
          <InlineAlert tone="danger">
            지금은 데이터 상태를 확인할 수 없습니다. 잠시 후 다시 시도해 주세요.
          </InlineAlert>
        ) : (
          <div className={styles.current}>
            {/* 색만으로 상태를 전달하지 않는다 — 라벨이 계약상 필수다. */}
            <StatusBadge
              status={freshness.fresh ? "fresh" : "stale"}
              label={freshness.fresh ? "정상 반영" : "반영 지연"}
            />
            <p>
              {freshness.latestRound}회(
              <time dateTime={freshness.latestDrawDate}>
                {formatDrawDate(freshness.latestDrawDate)}
              </time>
              ) 추첨분까지 반영돼 있습니다.
            </p>
            <p className={styles.note}>
              마지막 확인:{" "}
              <time dateTime={freshness.checkedAt}>{formatDateTime(freshness.checkedAt)}</time>
            </p>
          </div>
        )}
      </section>

      <section aria-labelledby="incidents" className="stack">
        <h2 id="incidents">최근 30일 수집·보정 이력</h2>
        {incidents === null ? (
          <InlineAlert tone="danger">지금은 이력을 불러올 수 없습니다.</InlineAlert>
        ) : incidents.length === 0 ? (
          <EmptyState
            reason="no-data"
            title="기록된 이력이 없습니다"
            description="최근 30일 동안 수집 지연이나 보정이 없었습니다."
          />
        ) : (
          <Table caption="최근 30일 수집·보정 이력" captionVisible={false}>
            <thead>
              <tr>
                <th scope="col">회차</th>
                <th scope="col">유형</th>
                <th scope="col">상태</th>
                <th scope="col">발생 시각</th>
              </tr>
            </thead>
            <tbody>
              {incidents.map((incident) => (
                <tr key={`${incident.type}-${incident.round}-${incident.occurredAt}`}>
                  <th scope="row">{incident.round === null ? "—" : `${incident.round}회`}</th>
                  <td>
                    {incidentTypeLabel(incident.type)}
                    {incident.occurrences > 1 && ` (시도 ${incident.occurrences}회)`}
                  </td>
                  <td>
                    <StatusBadge
                      status={incident.resolved ? "fresh" : "stale"}
                      label={incident.resolved ? "해결됨" : "확인 중"}
                    />
                  </td>
                  <td>
                    <time dateTime={incident.occurredAt}>
                      {formatDateTime(incident.occurredAt)}
                    </time>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </section>
    </div>
  );
}
