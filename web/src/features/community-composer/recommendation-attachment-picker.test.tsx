import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionContext, type SessionState } from "@/entities/user-session/session-context";

import { RecommendationAttachmentPicker } from "./recommendation-attachment-picker";

const { listDeviceRecommendationSets, listAccountRecommendationSets } = vi.hoisted(() => ({
  listDeviceRecommendationSets: vi.fn(),
  listAccountRecommendationSets: vi.fn(),
}));

vi.mock("@/entities/recommendation/api", () => ({
  listDeviceRecommendationSets,
  listAccountRecommendationSets,
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

function set(id: number) {
  return {
    id,
    strategy: "random" as const,
    createdAt: "2026-08-01T12:00:00Z",
    historyThroughRound: 1149,
    items: [{ position: 0, numbers: [1, 2, 3, 4, 5, 6], score: null, explanationCodes: [] }],
  };
}

function page(items: ReturnType<typeof set>[]) {
  return { items, page: 0, totalPages: 1, totalElements: items.length };
}

function renderPicker(session: SessionState, value: number | null = null, onChange = vi.fn()) {
  return {
    onChange,
    ...render(
      <SessionContext.Provider value={session}>
        <RecommendationAttachmentPicker value={value} onChange={onChange} />
      </SessionContext.Provider>,
    ),
  };
}

describe("추천 첨부 피커", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("로그인 상태면 계정 스코프 API를 호출한다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page([set(1)]));

    renderPicker(LOGGED_IN);

    await waitFor(() => expect(listAccountRecommendationSets).toHaveBeenCalledWith(0));
    expect(listDeviceRecommendationSets).not.toHaveBeenCalled();
  });

  it("비로그인이면 기기 스코프 API를 호출한다", async () => {
    listDeviceRecommendationSets.mockResolvedValue(page([set(1)]));

    renderPicker(ANONYMOUS);

    await waitFor(() => expect(listDeviceRecommendationSets).toHaveBeenCalledWith(0));
    expect(listAccountRecommendationSets).not.toHaveBeenCalled();
  });

  it("불러오기 실패 시 다시 시도 버튼을 보여준다", async () => {
    listAccountRecommendationSets.mockRejectedValue(new Error("network"));

    renderPicker(LOGGED_IN);

    expect(await screen.findByText("추천 세트를 불러오지 못했습니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });

  it("세트가 없으면 빈 상태를 보여준다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page([]));

    renderPicker(LOGGED_IN);

    expect(await screen.findByText("첨부할 수 있는 추천 세트가 없습니다")).toBeInTheDocument();
  });

  it("세트를 선택하면 onChange(setId)를 호출한다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page([set(1), set(2)]));
    const user = userEvent.setup();
    const { onChange } = renderPicker(LOGGED_IN);

    const radios = await screen.findAllByRole("radio");
    // 첫 옵션은 "첨부 안 함"이므로 두 번째(첫 세트)를 선택한다(fixture가 개수를 보장).
    await user.click(radios[1] as HTMLElement);

    expect(onChange).toHaveBeenCalledWith(1);
  });

  it("'첨부 안 함'을 선택하면 onChange(null)을 호출한다", async () => {
    listAccountRecommendationSets.mockResolvedValue(page([set(1)]));
    const user = userEvent.setup();
    const { onChange } = renderPicker(LOGGED_IN, 1);

    await user.click(await screen.findByRole("radio", { name: "첨부 안 함" }));

    expect(onChange).toHaveBeenCalledWith(null);
  });
});
