import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";
import { resetResourceCacheForTests } from "@/shared/hooks/use-resource";

import { RecommendHistoryList } from "./recommend-history-list";

const {
  listDeviceRecommendationSets,
  listAccountRecommendationSets,
  deleteDeviceRecommendationSet,
  deleteAccountRecommendationSet,
} = vi.hoisted(() => ({
  listDeviceRecommendationSets: vi.fn(),
  listAccountRecommendationSets: vi.fn(),
  deleteDeviceRecommendationSet: vi.fn(),
  deleteAccountRecommendationSet: vi.fn(),
}));

vi.mock("@/entities/recommendation/api", () => ({
  listDeviceRecommendationSets,
  listAccountRecommendationSets,
  deleteDeviceRecommendationSet,
  deleteAccountRecommendationSet,
}));

const ANONYMOUS: SessionState = {
  session: { loggedIn: false, userId: null, nickname: null, activeProviders: [] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

const LOGGED_IN: SessionState = {
  session: { loggedIn: true, userId: 10, nickname: "나", activeProviders: ["google"] },
  loading: false,
  error: false,
  claimStatus: "settled",
  retry: vi.fn(),
};

function set(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1,
    strategy: "random",
    createdAt: "2026-08-01T12:00:00Z",
    historyThroughRound: 1149,
    items: [{ position: 0, numbers: [1, 2, 3, 4, 5, 6], score: null, explanationCodes: [] }],
    ...overrides,
  };
}

function page(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    items: [set()],
    page: 0,
    totalPages: 1,
    totalElements: 1,
    ...overrides,
  };
}

function renderList(session: SessionState) {
  return render(
    <SessionContext.Provider value={session}>
      <RecommendHistoryList />
    </SessionContext.Provider>,
  );
}

describe("추천 이력", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // KF-22(docs/improvement.md): useResource의 캐시가 모듈 스코프라 테스트
    // 간에 새지 않게 리셋한다.
    resetResourceCacheForTests();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("익명은 기기 스코프로 조회한다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page());
    renderList(ANONYMOUS);

    await waitFor(() =>
      expect(listDeviceRecommendationSets).toHaveBeenCalledWith(0, expect.any(AbortSignal)),
    );
    expect(listAccountRecommendationSets).not.toHaveBeenCalled();
  });

  it("로그인 사용자는 계정 스코프로 조회한다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page());
    renderList(LOGGED_IN);

    await waitFor(() =>
      expect(listAccountRecommendationSets).toHaveBeenCalledWith(0, expect.any(AbortSignal)),
    );
    expect(listDeviceRecommendationSets).not.toHaveBeenCalled();
  });

  // KF-22(docs/improvement.md): 세 소비자를 useResource로 이관하며 loggedIn을
  // 캐시 키에 접었다 — 익명으로 보던 중 로그인(claim)이 끝나면 새 키로 자동
  // 재조회되는지 확인한다(saved-library.test.tsx의 동등한 회귀 테스트와 짝).
  it("익명으로 보던 중 로그인(claim)이 끝나면 계정 스코프로 다시 조회한다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page({ items: [set({ id: 1 })] }));
    listAccountRecommendationSets.mockResolvedValue(page({ items: [set({ id: 2 })] }));

    const { rerender } = render(
      <SessionContext.Provider value={ANONYMOUS}>
        <RecommendHistoryList />
      </SessionContext.Provider>,
    );
    await waitFor(() => expect(listDeviceRecommendationSets).toHaveBeenCalledTimes(1));
    expect(listAccountRecommendationSets).not.toHaveBeenCalled();

    rerender(
      <SessionContext.Provider value={LOGGED_IN}>
        <RecommendHistoryList />
      </SessionContext.Provider>,
    );

    await waitFor(() => expect(listAccountRecommendationSets).toHaveBeenCalledTimes(1));
    expect(listDeviceRecommendationSets).toHaveBeenCalledTimes(1);
  });

  it("빈 이력이면 추천받기 안내를 보여준다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page({ items: [], totalElements: 0 }));
    renderList(ANONYMOUS);

    expect(await screen.findByText("아직 생성한 추천이 없습니다")).toBeInTheDocument();
  });

  it("더 볼 페이지가 있으면 더 보기 버튼을 보여준다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page({ totalPages: 2 }));
    renderList(ANONYMOUS);

    expect(await screen.findByRole("button", { name: "더 보기" })).toBeInTheDocument();
  });

  it("마지막 페이지면 전체 표시 문구만 보여준다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page());
    renderList(ANONYMOUS);

    expect(await screen.findByText("전체 1건을 모두 표시했습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });

  // KF-13(docs/improvement.md): loadMore()에 catch가 없어 거부가 미처리 프로미스로
  // 새고 UI는 아무 안내도 없이 그냥 로딩만 풀렸다.
  it("KF-13: 더 보기 실패 시 인라인 에러를 보이고 기존 행을 유지하며, 재시도하면 같은 커서로 이어붙는다", async () => {
    const user = userEvent.setup();
    listDeviceRecommendationSets.mockResolvedValueOnce(page({ totalPages: 2 }));
    listDeviceRecommendationSets.mockRejectedValueOnce(new Error("network"));
    listDeviceRecommendationSets.mockResolvedValueOnce(
      page({ page: 1, totalPages: 2, items: [set({ id: 2 })], totalElements: 2 }),
    );

    renderList(ANONYMOUS);
    await user.click(await screen.findByRole("button", { name: "더 보기" }));

    expect(
      await screen.findByText("더 보기를 불러오지 못했습니다. 다시 시도해 주세요."),
    ).toBeInTheDocument();
    expect(listDeviceRecommendationSets).toHaveBeenLastCalledWith(1);
    // 실패해도 기존 행은 그대로다.
    expect(screen.getAllByText("이 세트 삭제")).toHaveLength(1);

    await user.click(screen.getByRole("button", { name: "더 보기" }));

    await waitFor(() => expect(listDeviceRecommendationSets).toHaveBeenLastCalledWith(1));
    expect(
      screen.queryByText("더 보기를 불러오지 못했습니다. 다시 시도해 주세요."),
    ).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText("이 세트 삭제")).toHaveLength(2));
  });

  it("삭제를 확인하면 해당 스코프의 삭제 API를 부른다", async () => {
    const user = userEvent.setup();
    listAccountRecommendationSets.mockResolvedValue(page());
    deleteAccountRecommendationSet.mockResolvedValue(null);

    renderList(LOGGED_IN);
    await user.click(await screen.findByText("이 세트 삭제"));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deleteAccountRecommendationSet).toHaveBeenCalledWith(1));
    expect(deleteDeviceRecommendationSet).not.toHaveBeenCalled();
  });
});
