import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

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
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("익명은 기기 스코프로 조회한다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page());
    renderList(ANONYMOUS);

    await waitFor(() => expect(listDeviceRecommendationSets).toHaveBeenCalledWith(0));
    expect(listAccountRecommendationSets).not.toHaveBeenCalled();
  });

  it("로그인 사용자는 계정 스코프로 조회한다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page());
    renderList(LOGGED_IN);

    await waitFor(() => expect(listAccountRecommendationSets).toHaveBeenCalledWith(0));
    expect(listDeviceRecommendationSets).not.toHaveBeenCalled();
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
