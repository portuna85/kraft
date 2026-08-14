"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { ApiError, toApiError } from "@/shared/api/error";

/**
 * 사용자별 서버 상태를 위한 최소 캐시
 *
 * TanStack Query를 도입하지 않는 이유는 예산이다(§8.2: ~13KB로 라우트 여유의 대부분을
 * 소진). 그런데 이 앱에서 클라이언트 전용 조회는 5개뿐이다 — 세션, 내 상호작용, 차단
 * 목록, 보관함, 추천 이력. 나머지는 전부 RSC가 가져온다. 그 5개를 위해 필요한 기능만
 * 여기 있다: **요청 중복 제거 · TTL 캐시 · 무효화 · 언마운트 abort**.
 *
 * 없는 기능: 백그라운드 재검증, 무한 스크롤, 폴링, 윈도우 포커스 재조회.
 * 이것들이 필요해지면 그때 예산 재협상과 함께 라이브러리 도입을 안건화한다(§8.3).
 *
 * **오류를 빈 데이터로 축약하지 않는다.** 조회 실패와 "결과 없음"이 같은 화면이 되면
 * 사용자는 재시도할 이유를 알 수 없다(§6.5 승계, 레거시 FE-053).
 */

export type ResourceState<T> =
  /** key가 null — 아직 조회할 조건이 아니다(조건부 조회) */
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: T }
  | { status: "error"; error: ApiError; retry: () => void };

type Entry = {
  at: number;
  promise: Promise<unknown>;
  controller: AbortController;
  /** 이 키를 구독 중인 컴포넌트 수. 0이 되면 **진행 중인** 요청만 abort한다. */
  refCount: number;
  /** 이미 끝난 요청은 캐시로서 가치가 있다 — 구독자가 사라져도 TTL까지 남긴다. */
  settled: boolean;
};

const cache = new Map<string, Entry>();
const listeners = new Map<string, Set<() => void>>();

const DEFAULT_TTL_MS = 30_000;

function subscribe(key: string, listener: () => void): () => void {
  const set = listeners.get(key) ?? new Set();
  set.add(listener);
  listeners.set(key, set);

  return () => {
    set.delete(listener);
    if (set.size === 0) listeners.delete(key);
  };
}

/**
 * 접두사로 시작하는 캐시를 전부 버리고 구독자에게 재조회를 알린다.
 * 로그아웃·탈퇴 시 사용자별 캐시를 통째로 비우는 데 쓴다(§15.2) — 남겨두면 다음
 * 사용자에게 이전 사용자의 데이터가 잠깐 보인다.
 */
export function invalidateResource(keyPrefix: string): void {
  for (const key of [...cache.keys()]) {
    if (key.startsWith(keyPrefix)) cache.delete(key);
  }
  for (const [key, set] of listeners) {
    if (key.startsWith(keyPrefix)) {
      for (const listener of set) listener();
    }
  }
}

/** 테스트 격리용. 프로덕션 코드에서 부르지 않는다. */
export function resetResourceCacheForTests(): void {
  cache.clear();
  listeners.clear();
}

export function useResource<T>(
  key: string | null,
  load: (signal: AbortSignal) => Promise<T>,
  options: { ttlMs?: number } = {},
): ResourceState<T> {
  const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
  // 결과를 "어떤 키·몇 번째 시도의 결과인지"와 함께 들고 있는다. 그래야 키가 바뀐
  // 직후에 이전 키의 데이터가 한 프레임 보이는 일이 없고, 이펙트 안에서 loading으로
  // 되돌리는 동기 setState(불필요한 연쇄 렌더)도 없앨 수 있다.
  const [settled, setSettled] = useState<{
    key: string;
    attempt: number;
    state: ResourceState<T>;
  } | null>(null);
  const [attempt, setAttempt] = useState(0);

  // load가 인라인 함수로 넘어오는 것이 정상이므로 의존성에 넣지 않는다 — 넣으면
  // 매 렌더 재조회가 돈다(레거시 FE-099와 같은 함정). 렌더 중에 ref를 쓰면 안 되므로
  // 갱신은 이펙트에서 한다.
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  const retry = useCallback(() => {
    if (key !== null) cache.delete(key);
    setAttempt((current) => current + 1);
  }, [key]);

  useEffect(() => {
    if (key === null) return;

    let cancelled = false;

    const cached = cache.get(key);
    const isFresh = cached !== undefined && Date.now() - cached.at < ttlMs;

    let entry: Entry;
    if (isFresh) {
      entry = cached;
    } else {
      const controller = new AbortController();
      entry = {
        at: Date.now(),
        controller,
        refCount: 0,
        settled: false,
        promise: loadRef.current(controller.signal),
      };
      cache.set(key, entry);
      entry.promise.then(
        () => {
          entry.settled = true;
        },
        () => {
          // 실패는 캐시에 남기지 않는다 — 남기면 TTL 동안 재시도해도 같은 오류가 나온다.
          entry.settled = true;
          if (cache.get(key) === entry) cache.delete(key);
        },
      );
    }

    entry.refCount += 1;

    entry.promise.then(
      (data) => {
        if (!cancelled) setSettled({ key, attempt, state: { status: "success", data: data as T } });
      },
      (cause: unknown) => {
        if (cancelled) return;
        setSettled({
          key,
          attempt,
          state: {
            status: "error",
            error: toApiError(cause, "정보를 불러오지 못했습니다."),
            retry,
          },
        });
      },
    );

    const unsubscribe = subscribe(key, () => setAttempt((current) => current + 1));

    return () => {
      cancelled = true;
      unsubscribe();
      entry.refCount -= 1;
      // 마지막 구독자가 사라졌고 **아직 끝나지 않은** 요청만 끊는다. 무조건 abort하면
      // 같은 키를 함께 보던 다른 컴포넌트의 요청까지 죽고, 끝난 응답까지 버리면
      // TTL 캐시가 아무 일도 하지 않는다.
      if (entry.refCount <= 0 && !entry.settled && cache.get(key) === entry) {
        entry.controller.abort();
        cache.delete(key);
      }
    };
  }, [key, ttlMs, attempt, retry]);

  if (key === null) return { status: "idle" };
  // 아직 이번 키·이번 시도의 결과가 아니면 로딩이다 — 이전 키의 데이터를 보여주지 않는다.
  if (settled === null || settled.key !== key || settled.attempt !== attempt) {
    return { status: "loading" };
  }
  return settled.state;
}
