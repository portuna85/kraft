"use client";

import { useEffect, useState } from "react";

import {
  deleteAccountRecommendationSet,
  deleteDeviceRecommendationSet,
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
} from "@/entities/recommendation/api";
import type { RecommendationSet } from "@/entities/recommendation/schema";
import { RecommendationCard } from "@/entities/recommendation/ui/recommendation-card";
import {
  canQueryOwnerScope,
  sessionReadiness,
  useSession,
} from "@/entities/user-session/session-context";
import { ROUTES } from "@/shared/config/routes";
import { useResource } from "@/shared/hooks/use-resource";
import { Button, LinkButton } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { ListRowsSkeleton } from "@/shared/ui/page-skeleton";
import { EmptyState, ErrorState, InlineAlert } from "@/shared/ui/states";

import styles from "./history.module.css";

/**
 * 추천 이력
 *
 * 저장 번호·추천 이력 모두 같은 두 갈래 규칙을 따른다 — 익명은 기기 토큰 스코프,
 * 로그인은 세션 스코프이며 절대 섞이지 않는다(§3.3, 백엔드 B-P0-2). 최신 순으로 20개씩
 * "더 보기"로 이어 받는다(레거시 FE-021 — 예전에는 50개 한 번뿐이라 51번째부터
 * 접근 경로가 없었다).
 */
export function RecommendHistoryList() {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const sessionReady = sessionReadiness(session) !== "unsettled";

  /**
   * KF-22(docs/improvement.md): 최초 0페이지 로드만 useResource에 맡긴다 —
   * "더 보기"는 페이지를 이어붙이는 누적 모델이라 useResource의 단일
   * 키-단일 값 캐시와 안 맞아 그대로 수동 fetch로 둔다(아래 loadMore).
   */
  const resourceKey = sessionReady ? `me:history:${loggedIn}` : null;
  const resource = useResource(resourceKey, (signal) =>
    loggedIn ? listAccountRecommendationSets(0, signal) : listDeviceRecommendationSets(0, signal),
  );
  const loadError = resource.status === "error";
  const firstPage = resource.status === "success" ? resource.data : null;

  const [items, setItems] = useState<RecommendationSet[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<RecommendationSet | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // 스코프가 바뀌면(키 변경) 이전 스코프의 목록을 잠깐이라도 보여주지 않는다 —
  // 새 키의 결과가 도착할 때까지 스켈레톤으로 되돌아간다. 이펙트 본문에서
  // setState를 직접 트리거하지 않는다 — 마이크로태스크로 미룬다
  // (identity-session/session-provider.tsx와 같은 관용구).
  useEffect(() => {
    void Promise.resolve().then(() => setItems(null));
  }, [resourceKey]);

  // 0페이지 응답이 도착하면 로컬 상태로 옮겨 심는다 — 이후 loadMore가 이
  // 로컬 상태를 계속 이어붙인다.
  useEffect(() => {
    if (firstPage === null) return;
    void Promise.resolve().then(() => {
      setItems(firstPage.items);
      setPage(firstPage.page);
      setTotalPages(firstPage.totalPages);
      setTotal(firstPage.totalElements);
    });
  }, [firstPage]);

  // KF-13(docs/improvement.md): catch가 없어 거부가 미처리 프로미스로 새고
  // 로딩만 리셋됐다 — 이미 로드된 행·커서(page)는 그대로 둬서 같은 요청으로
  // 재시도할 수 있게 한다.
  async function loadMore() {
    setLoadingMore(true);
    setLoadMoreError(false);
    try {
      const result = loggedIn
        ? await listAccountRecommendationSets(page + 1)
        : await listDeviceRecommendationSets(page + 1);
      setItems((current) => [...(current ?? []), ...result.items]);
      setPage(result.page);
      setTotalPages(result.totalPages);
      setTotal(result.totalElements);
    } catch {
      setLoadMoreError(true);
    } finally {
      setLoadingMore(false);
    }
  }

  async function confirmDelete() {
    if (deleteTarget === null) return;
    setDeletePending(true);
    setDeleteError(null);
    try {
      if (loggedIn) await deleteAccountRecommendationSet(deleteTarget.id);
      else await deleteDeviceRecommendationSet(deleteTarget.id);
      setItems((current) => current?.filter((item) => item.id !== deleteTarget.id) ?? null);
      setTotal((count) => Math.max(0, count - 1));
      setDeleteTarget(null);
    } catch {
      setDeleteError("삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setDeletePending(false);
    }
  }

  if (loadError) {
    return (
      <ErrorState
        title="추천 이력을 불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        action={
          <Button
            variant="secondary"
            onClick={() => resource.status === "error" && resource.retry()}
          >
            다시 시도
          </Button>
        }
      />
    );
  }

  // KF-08(docs/improvement.md): 평문 한 줄이었다가 목록이 도착하면 N행으로
  // 급팽창해 푸터가 크게 밀렸다(실측 CLS 0.1158) — 스켈레톤으로 미리 높이를
  // 예약해 급변 폭을 줄인다. 기본 8행 스켈레톤을 그대로 쓰면 가장 흔한 케이스
  // (항목 0개, CI 픽스처 기본값과 같음)에서 EmptyState로 무너질 때 오히려 이전보다
  // 큰 시프트(실측 CLS 0.414)를 만든다 — EmptyState 높이에 더 가깝게 행 수를
  // 줄여 과잉 예약을 막는다.
  if (items === null) return <ListRowsSkeleton rows={1} />;

  if (items.length === 0) {
    return (
      <EmptyState
        reason="no-data"
        title="아직 생성한 추천이 없습니다"
        description="번호 추천에서 조합을 만들면 여기에 쌓입니다."
        action={<LinkButton href={ROUTES.recommend}>번호 추천 받기</LinkButton>}
      />
    );
  }

  return (
    <div className="stack">
      <ol className={styles.list}>
        {items.map((set) => (
          <li key={set.id}>
            <RecommendationCard
              strategy={set.strategy}
              createdAt={set.createdAt}
              historyThroughRound={set.historyThroughRound}
              items={set.items}
            />
            <Button variant="quiet" onClick={() => setDeleteTarget(set)}>
              이 세트 삭제
            </Button>
          </li>
        ))}
      </ol>

      {page + 1 < totalPages ? (
        <div className={styles.more}>
          {loadingMore ? (
            <Button variant="secondary" loading loadingLabel="불러오는 중">
              더 보기
            </Button>
          ) : (
            <Button variant="secondary" onClick={loadMore}>
              더 보기
            </Button>
          )}
          <p className={styles.note} role="status" aria-live="polite">
            전체 {total}건 중 {items.length}건 표시
          </p>
          {loadMoreError && (
            <InlineAlert tone="danger">
              더 보기를 불러오지 못했습니다. 다시 시도해 주세요.
            </InlineAlert>
          )}
        </div>
      ) : (
        <p className={styles.note} role="status" aria-live="polite">
          전체 {total}건을 모두 표시했습니다.
        </p>
      )}

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title="추천 세트를 삭제할까요?"
        variant="danger"
        description="삭제한 추천 이력은 되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={confirmDelete}
        pending={deletePending}
        errorMessage={deleteError}
      />
    </div>
  );
}
