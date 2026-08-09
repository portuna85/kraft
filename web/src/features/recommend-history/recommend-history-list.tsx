"use client";

import { useCallback, useEffect, useState } from "react";

import {
  deleteAccountRecommendationSet,
  deleteDeviceRecommendationSet,
  listAccountRecommendationSets,
  listDeviceRecommendationSets,
} from "@/entities/recommendation/api";
import type { RecommendationSet } from "@/entities/recommendation/schema";
import { RecommendationCard } from "@/entities/recommendation/ui/recommendation-card";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { ROUTES } from "@/shared/config/routes";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { EmptyState, ErrorState } from "@/shared/ui/states";

import styles from "./history.module.css";

/**
 * 추천 이력 — improvement_fe.md §23.8, §25.5
 *
 * 저장 번호·추천 이력 모두 같은 두 갈래 규칙을 따른다 — 익명은 기기 토큰 스코프,
 * 로그인은 세션 스코프이며 절대 섞이지 않는다(§3.3, 백엔드 B-P0-2). 최신 순으로 20개씩
 * "더 보기"로 이어 받는다(레거시 FE-021 — 예전에는 50개 한 번뿐이라 51번째부터
 * 접근 경로가 없었다).
 */
export function RecommendHistoryList() {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);

  const [items, setItems] = useState<RecommendationSet[] | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loadError, setLoadError] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<RecommendationSet | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoadError(false);
    try {
      const result = loggedIn
        ? await listAccountRecommendationSets(0)
        : await listDeviceRecommendationSets(0);
      setItems(result.items);
      setPage(result.page);
      setTotalPages(result.totalPages);
      setTotal(result.totalElements);
    } catch {
      setItems(null);
      setLoadError(true);
    }
  }, [loggedIn]);

  useEffect(() => {
    if (session.loading) return;

    let cancelled = false;
    // 이펙트 본문에서 setState를 직접 트리거하지 않는다 — 마이크로태스크로 미룬다
    // (identity-session/session-provider.tsx와 같은 관용구).
    void Promise.resolve().then(() => {
      if (!cancelled) void load();
    });

    return () => {
      cancelled = true;
    };
  }, [load, session.loading]);

  async function loadMore() {
    setLoadingMore(true);
    try {
      const result = loggedIn
        ? await listAccountRecommendationSets(page + 1)
        : await listDeviceRecommendationSets(page + 1);
      setItems((current) => [...(current ?? []), ...result.items]);
      setPage(result.page);
      setTotalPages(result.totalPages);
      setTotal(result.totalElements);
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
          <Button variant="secondary" onClick={() => void load()}>
            다시 시도
          </Button>
        }
      />
    );
  }

  if (items === null) return <p className={styles.note}>추천 이력을 불러오는 중입니다…</p>;

  if (items.length === 0) {
    return (
      <EmptyState
        reason="no-data"
        title="아직 생성한 추천이 없습니다"
        description="번호 추천에서 조합을 만들면 여기에 쌓입니다."
        action={<a href={ROUTES.recommend}>번호 추천받기</a>}
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
