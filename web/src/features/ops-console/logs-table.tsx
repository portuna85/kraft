"use client";

import { useState } from "react";

import { getOpsLogs } from "@/entities/ops/api";
import {
  OPERATION_STATUS_LABELS,
  OPERATION_TYPE_LABELS,
  type OperationLogPage,
} from "@/entities/ops/schema";
import { ApiError } from "@/shared/api/error";
import { formatDateTime } from "@/shared/lib/format";
import { Badge, StatusBadge } from "@/shared/ui/badge";
import { Button } from "@/shared/ui/button";
import { EmptyState, InlineAlert } from "@/shared/ui/states";
import { Table } from "@/shared/ui/surface";

import styles from "./ops-console.module.css";

const PAGE_SIZE = 20;

/**
 * 운영 로그 테이블 ⑤.
 *
 * 페이지 이동에 `shared/ui/surface.tsx`의 기존 `Pagination`(URL Link 기반)을
 * 그대로 쓰지 않는다 — 이 화면은 토큰을 URL이 아니라 컴포넌트 state로만 들고
 * 있어 URL에 목록 상태를 반영할 이유가 없고(딥링크 가치가 없다), Link 기반
 * 페이지 이동은 이 컴포넌트를 다른 패널과 다른 방식(URL 상태)으로 만들어
 * 일관성이 깨진다. 대신 같은 파일의 `Table` 재사용 + 로컬 state로 페이지를
 * 넘긴다.
 *
 * 필터(operationType·executionStatus·round·기간)는 넣지 않는다 — 문서가
 * 명시하지 않았고, 페이지네이션 있는 최신 로그 목록만으로 "결과 패널"의
 * 취지는 충족된다.
 */
export function LogsTable({ token }: { token: string }) {
  const [logs, setLogs] = useState<OperationLogPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tokenReady = token.trim().length > 0;

  async function loadPage(page: number) {
    setLoading(true);
    setError(null);
    try {
      const result = await getOpsLogs(token, { page, size: PAGE_SIZE });
      setLogs(result);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "이력을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="stack">
      <div className={styles.tokenRow}>
        {loading ? (
          <Button loading loadingLabel="조회 중">
            이력 조회
          </Button>
        ) : (
          <Button onClick={() => void loadPage(0)} disabled={!tokenReady}>
            이력 조회
          </Button>
        )}
      </div>

      {/* KF-23(docs/improvement.md): 평문 <p>라 스크린리더에 무성이었다 —
          role="alert"가 있는 InlineAlert로 바꾼다. "이력 조회" 버튼이 이미
          재시도 경로라 별도 액션은 추가하지 않는다. */}
      {error !== null && <InlineAlert tone="danger">{error}</InlineAlert>}

      {logs !== null &&
        (logs.items.length === 0 ? (
          <EmptyState
            reason="no-data"
            title="기록된 이력이 없습니다"
            description="아직 수집·수동 적재 이력이 없습니다."
          />
        ) : (
          <>
            <Table caption="운영 이력">
              <thead>
                <tr>
                  <th scope="col">회차</th>
                  <th scope="col">유형</th>
                  <th scope="col">상태</th>
                  <th scope="col">발생 시각</th>
                  <th scope="col">메시지</th>
                </tr>
              </thead>
              <tbody>
                {logs.items.map((log) => (
                  <tr key={log.id}>
                    <th scope="row">{log.round === null ? "—" : `${log.round}회`}</th>
                    <td>
                      <Badge tone="neutral" label={OPERATION_TYPE_LABELS[log.operationType]} />
                    </td>
                    <td>
                      <StatusBadge
                        status={log.executionStatus === "SUCCESS" ? "fresh" : "error"}
                        label={OPERATION_STATUS_LABELS[log.executionStatus]}
                      />
                    </td>
                    <td>
                      <time dateTime={log.createdAt}>{formatDateTime(log.createdAt)}</time>
                    </td>
                    <td>{log.message ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </Table>

            {logs.totalPages > 1 && (
              <div className={styles.roundRow}>
                <Button
                  variant="quiet"
                  onClick={() => void loadPage(logs.page - 1)}
                  disabled={loading || logs.page <= 0}
                >
                  이전
                </Button>
                <span>
                  {logs.page + 1} / {logs.totalPages}
                </span>
                <Button
                  variant="quiet"
                  onClick={() => void loadPage(logs.page + 1)}
                  disabled={loading || logs.page >= logs.totalPages - 1}
                >
                  다음
                </Button>
              </div>
            )}
          </>
        ))}
    </div>
  );
}
