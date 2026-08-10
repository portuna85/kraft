"use client";

import { useCallback, useEffect, useState } from "react";

import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import {
  deleteAccountSavedNumber,
  deleteDeviceSavedNumber,
  listAccountSavedNumbers,
  listDeviceSavedNumbers,
  matchAccountSavedNumbers,
  matchDeviceSavedNumbers,
} from "@/entities/saved-number/api";
import type { SavedNumber, SavedNumberMatch } from "@/entities/saved-number/schema";
import { MatchResultBadge } from "@/entities/saved-number/ui/match-result-badge";
import { canQueryOwnerScope, useSession } from "@/entities/user-session/session-context";
import { ROUTES } from "@/shared/config/routes";
import { formatDrawDate } from "@/shared/lib/format";
import { Button, LinkButton } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { EmptyState, ErrorState } from "@/shared/ui/states";
import { Card } from "@/shared/ui/surface";

import styles from "./library.module.css";

/**
 * 보관함 — improvement_fe.md §23.7, §25.5
 *
 * **로그인 여부가 엔드포인트를 통째로 가른다.** 익명은 기기 토큰 스코프, 로그인은 세션
 * 스코프다. 섞을 수 없는 이유는 백엔드 B-P0-3에 있다 — claim이 끝나면 저장 번호의
 * client_token_hash가 지워져 기기 경로로는 더 이상 찾지 못한다.
 *
 * **이 화면은 디바이스 토큰을 만들거나 회전시키지 않는다.** 그 일은 identity-session이
 * 전담한다(R-1). 여기서 토큰을 건드리면 익명 사용자의 저장 기록이 통째로 고립될 수 있다.
 */
export function SavedLibrary({ latestRound }: { latestRound: number }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);

  const [items, setItems] = useState<SavedNumber[] | null>(null);
  const [matches, setMatches] = useState<Map<number, SavedNumberMatch> | null>(null);
  const [round, setRound] = useState(latestRound);
  const [loadError, setLoadError] = useState(false);
  const [matching, setMatching] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SavedNumber | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoadError(false);
    // 로그인 상태가 바뀌면 대조 결과도 의미가 없어진다.
    setMatches(null);
    try {
      setItems(loggedIn ? await listAccountSavedNumbers() : await listDeviceSavedNumbers());
    } catch {
      setItems(null);
      setLoadError(true);
    }
  }, [loggedIn]);

  useEffect(() => {
    // 세션 판정이 끝나기 전에 조회하면 익명 경로로 잘못 나간다.
    if (session.loading) return;

    let cancelled = false;
    // 이펙트 본문에서 setState를 직접(동기로) 트리거하지 않는다 — 마이크로태스크로
    // 미룬다(identity-session/session-provider.tsx와 같은 관용구,
    // react-hooks/set-state-in-effect).
    void Promise.resolve().then(() => {
      if (!cancelled) void load();
    });

    return () => {
      cancelled = true;
    };
  }, [load, session.loading]);

  async function compare() {
    setMatching(true);
    try {
      const results = loggedIn
        ? await matchAccountSavedNumbers(String(round))
        : await matchDeviceSavedNumbers(String(round));
      setMatches(new Map(results.map((result) => [result.savedNumber.id, result])));
    } catch {
      setMatches(null);
    } finally {
      setMatching(false);
    }
  }

  async function confirmDelete() {
    if (deleteTarget === null) return;
    setDeletePending(true);
    setDeleteError(null);
    try {
      if (loggedIn) await deleteAccountSavedNumber(deleteTarget.id);
      else await deleteDeviceSavedNumber(deleteTarget.id);
      await load();
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
        title="보관함을 불러오지 못했습니다"
        description="잠시 후 다시 시도해 주세요."
        action={
          <Button variant="secondary" onClick={() => void load()}>
            다시 시도
          </Button>
        }
      />
    );
  }

  if (items === null) return <p className={styles.note}>보관함을 불러오는 중입니다…</p>;

  if (items.length === 0) {
    return (
      <EmptyState
        reason="no-data"
        title="아직 저장한 번호가 없습니다."
        description={
          loggedIn
            ? "번호를 저장하면 이 계정에 연결되어 어느 기기에서나 보입니다."
            : "저장한 번호는 이 브라우저에만 연결됩니다. 로그인하면 계정으로 옮겨집니다."
        }
        action={<LinkButton href={ROUTES.recommend}>번호 추천 받기</LinkButton>}
      />
    );
  }

  return (
    <div className="stack">
      <div className={styles.compare}>
        <label className={styles.roundLabel} htmlFor="round">
          대조할 회차
        </label>
        <input
          className={styles.roundInput}
          id="round"
          type="number"
          min={1}
          max={latestRound}
          value={round}
          onChange={(event) => setRound(Number(event.target.value))}
        />
        {matching ? (
          <Button loading loadingLabel="대조 중">
            대조하기
          </Button>
        ) : (
          <Button onClick={compare}>대조하기</Button>
        )}
      </div>

      <ol className={styles.list}>
        {items.map((item) => {
          const match = matches?.get(item.id);
          return (
            <Card as="li" level={2} key={item.id}>
              <div className={styles.item}>
                <div className={styles.itemHeader}>
                  <span className={styles.savedAt}>
                    {formatDrawDate(item.createdAt.slice(0, 10))}
                  </span>
                  {/* danger ghost — improvement_fe_codex.md §12.10 "delete는 danger ghost". */}
                  <Button variant="dangerQuiet" onClick={() => setDeleteTarget(item)}>
                    삭제
                  </Button>
                </div>

                <LottoBallSet numbers={item.numbers} />

                {match !== undefined && (
                  <MatchResultBadge
                    prizeTier={match.prizeTier}
                    matchedCount={match.matchedCount}
                    bonusMatch={match.bonusMatch}
                  />
                )}
              </div>
            </Card>
          );
        })}
      </ol>

      <ConfirmDialog
        open={deleteTarget !== null}
        onClose={() => setDeleteTarget(null)}
        title="저장한 번호를 지울까요?"
        variant="danger"
        description="지운 번호는 되돌릴 수 없습니다."
        confirmLabel="삭제"
        onConfirm={confirmDelete}
        pending={deletePending}
        errorMessage={deleteError}
      />
    </div>
  );
}
