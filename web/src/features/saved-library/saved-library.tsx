"use client";

import { useEffect, useState } from "react";

import { LottoBallSet } from "@/entities/round/ui/lotto-ball";
import {
  deleteAccountSavedNumber,
  deleteDeviceSavedNumber,
  listAccountSavedNumbers,
  listDeviceSavedNumbers,
  matchAccountSavedNumbers,
  matchDeviceSavedNumbers,
} from "@/entities/saved-number/api";
import {
  isTopPrize,
  type SavedNumber,
  type SavedNumberMatch,
} from "@/entities/saved-number/schema";
import { MatchResultBadge } from "@/entities/saved-number/ui/match-result-badge";
import {
  canQueryOwnerScope,
  sessionReadiness,
  useSession,
} from "@/entities/user-session/session-context";
import { ROUTES } from "@/shared/config/routes";
import { invalidateResource, useResource } from "@/shared/hooks/use-resource";
import { formatDrawDate } from "@/shared/lib/format";
import { Button, LinkButton } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/dialog";
import { ListRowsSkeleton } from "@/shared/ui/page-skeleton";
import { EmptyState, ErrorState, InlineAlert } from "@/shared/ui/states";
import { Card } from "@/shared/ui/surface";

import styles from "./library.module.css";
import { WinningCelebration } from "./winning-celebration";

/**
 * 보관함
 *
 * **로그인 여부가 엔드포인트를 통째로 가른다.** 익명은 기기 토큰 스코프, 로그인은 세션
 * 스코프다. 섞을 수 없는 이유는 백엔드 B-P0-3에 있다 — claim이 끝나면 저장 번호의
 * client_token_hash가 지워져 기기 경로로는 더 이상 찾지 못한다.
 *
 * **이 화면은 디바이스 토큰을 만들거나 회전시키지 않는다.** 그 일은 identity-session이
 * 전담한다(R-1). 여기서 토큰을 건드리면 익명 사용자의 저장 기록이 통째로 고립될 수 있다.
 */
export function SavedLibrary({ latestRound }: { latestRound: number | null }) {
  const session = useSession();
  const loggedIn = canQueryOwnerScope(session);
  const sessionReady = sessionReadiness(session) !== "unsettled";

  /**
   * KF-22(docs/improvement.md): `session.loading` 가드 + 마이크로태스크 지연 +
   * `cancelled` 플래그를 손으로 반복하던 걸 `useResource`에 맡긴다 — 세션이
   * 아직 미확정이면 `key`가 `null`이라 조회 자체가 안 나간다. `loggedIn`을
   * 키에 접어 스코프가 바뀌면(claim 등) 자동으로 새 키로 다시 조회되고,
   * 언마운트 시 미완료 요청이 abort된다(이전엔 셋 다 없었던 동작).
   */
  const resourceKey = sessionReady ? `me:saved:${loggedIn}` : null;
  const resource = useResource<SavedNumber[]>(resourceKey, (signal) =>
    loggedIn ? listAccountSavedNumbers(signal) : listDeviceSavedNumbers(signal),
  );
  const items = resource.status === "success" ? resource.data : null;
  const loadError = resource.status === "error";

  const [matches, setMatches] = useState<Map<number, SavedNumberMatch> | null>(null);
  const [celebrating, setCelebrating] = useState(false);
  // 목록이 (재)도착하면 대조 결과는 더 이상 의미가 없다 — 로그인 전환뿐
  // 아니라 삭제 후 재조회에도 적용돼야 해서 items 자체를 의존성으로 쓴다.
  useEffect(() => {
    void Promise.resolve().then(() => {
      setMatches(null);
      setCelebrating(false);
    });
  }, [items]);

  // KF-12(docs/improvement.md): 문자열로 들고 있어야 빈 입력(`Number("") === 0`이
  // 되는 함정)을 "숫자 0"과 구분해 판정할 수 있다.
  const [roundInput, setRoundInput] = useState(latestRound !== null ? String(latestRound) : "");
  const [matching, setMatching] = useState(false);
  const [compareError, setCompareError] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SavedNumber | null>(null);
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // KF-12(docs/improvement.md): `latestRound`를 모르면 대조 자체가 불가능하고,
  // 알아도 1~latestRound 범위를 벗어난 값을 서버로 보낼 이유가 없다 — 여기서
  // 막아야 버튼 비활성화·Enter 제출 양쪽이 같은 기준을 쓴다.
  const parsedRound = Number(roundInput);
  const isRoundValid =
    latestRound !== null &&
    roundInput.trim() !== "" &&
    Number.isInteger(parsedRound) &&
    parsedRound >= 1 &&
    parsedRound <= latestRound;

  async function compare() {
    if (!isRoundValid) return;
    setMatching(true);
    setCompareError(false);
    try {
      const results = loggedIn
        ? await matchAccountSavedNumbers(String(parsedRound))
        : await matchDeviceSavedNumbers(String(parsedRound));
      setMatches(new Map(results.map((result) => [result.savedNumber.id, result])));
      // 축하 연출은 대조를 눌러 결과가 막 나온 순간에만 튼다. 목록을 다시 그릴
      // 때마다 재생하면 삭제 한 번에도 콘페티가 쏟아진다.
      setCelebrating(results.some((result) => isTopPrize(result.prizeTier)));
    } catch {
      setMatches(null);
      setCompareError(true);
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
      // KF-22(docs/improvement.md): load()를 직접 다시 부르는 대신, 같은
      // 키를 구독 중인 useResource 인스턴스에 재조회를 알린다.
      if (resourceKey !== null) invalidateResource(resourceKey);
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
  // 급팽창해 푸터가 크게 밀렸다(실측 CLS 0.1174) — 스켈레톤으로 미리 높이를
  // 예약해 급변 폭을 줄인다. 기본 8행 스켈레톤을 그대로 쓰면 실제로 가장 흔한
  // 케이스(항목 0개, CI 픽스처 기본값과 같음)에서 EmptyState로 무너질 때 오히려
  // 이전보다 큰 시프트(실측 CLS 0.385)를 만든다 — EmptyState 높이에 더 가깝게
  // 행 수를 줄여 과잉 예약을 막는다.
  if (items === null) return <ListRowsSkeleton rows={1} />;

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
      {celebrating && <WinningCelebration />}

      {/* I-31: 회차 대조 폼 하나만으로는 컨텐츠 폭이 좁아 보관함 화면이 데스크톱
          절반을 비워둔 것처럼 보였다 — /analysis처럼 좁은 조작부와 넓은 목록을
          나란히 둔다. */}
      <div className={styles.layout}>
        <section aria-labelledby="compare" className="stack">
          <h2 id="compare">회차 대조</h2>
          <form
            className={styles.compare}
            onSubmit={(event) => {
              event.preventDefault();
              void compare();
            }}
          >
            <label className="note" htmlFor="round">
              대조할 회차
            </label>
            <input
              className={styles.roundInput}
              id="round"
              type="number"
              min={1}
              // KF-12(docs/improvement.md): 최신 회차를 모를 때 max=0처럼 불가능한
              // 값을 두지 않는다 — 아예 렌더하지 않는다.
              {...(latestRound !== null ? { max: latestRound } : {})}
              value={roundInput}
              disabled={latestRound === null}
              onChange={(event) => setRoundInput(event.target.value)}
            />
            {matching ? (
              <Button type="submit" loading loadingLabel="대조 중">
                대조하기
              </Button>
            ) : (
              <Button type="submit" disabled={!isRoundValid}>
                대조하기
              </Button>
            )}
          </form>
          {latestRound === null && (
            <InlineAlert tone="danger">
              최신 회차를 불러오지 못해 대조할 수 없습니다. 잠시 후 새로고침해 주세요.
            </InlineAlert>
          )}
          {compareError && (
            <InlineAlert tone="danger">
              대조하지 못했습니다. 잠시 후 다시 시도해 주세요.
            </InlineAlert>
          )}
        </section>

        <section aria-labelledby="saved-list" className={`stack ${styles.listRegion}`}>
          <h2 id="saved-list">저장한 번호</h2>
          <ol className={styles.list}>
            {items.map((item) => {
              const match = matches?.get(item.id);
              return (
                <Card as="li" level={2} key={item.id}>
                  <div className={styles.item}>
                    <div className={styles.itemHeader}>
                      <span className="note">{formatDrawDate(item.createdAt.slice(0, 10))}</span>
                      {/* danger ghost "delete는 danger ghost". */}
                      <Button variant="dangerQuiet" onClick={() => setDeleteTarget(item)}>
                        삭제
                      </Button>
                    </div>

                    <LottoBallSet
                      numbers={item.numbers}
                      matchedNumbers={
                        match !== undefined
                          ? new Set(
                              item.numbers.filter((value) => match.drawNumbers.includes(value)),
                            )
                          : undefined
                      }
                    />

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
        </section>
      </div>

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
