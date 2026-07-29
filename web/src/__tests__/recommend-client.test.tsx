import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { RecommendClient } from "@/components/recommend-client";

vi.mock("@/lib/device-token", () => ({
  getDeviceToken: () => "a".repeat(64),
}));

const EMPTY_RESULT = { recommendations: [], strategy: "random", algorithmVersion: "uniform-random-v1", historyThroughRound: 1189 };

function jsonResponse(body: unknown, ok = true, status = 200) {
  return { ok, status, json: () => Promise.resolve(body) };
}

describe("번호 추천 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(EMPTY_RESULT));
  });

  it("기본값으로 전략 선택·번호선택판·조합 수 입력을 렌더링한다", async () => {
    render(<RecommendClient />);

    await screen.findByRole("button", { name: "추천 생성" });
    expect(screen.getByRole("radio", { name: "무작위" })).toBeChecked();
    expect(screen.getByRole("group", { name: "1부터 45까지 번호 선택판" })).toBeInTheDocument();
    expect(screen.getByRole("spinbutton")).toHaveValue(5);
  });

  it("F-P0-6/7: 마운트만 해서는 추천을 요청하지 않는다", async () => {
    render(<RecommendClient />);

    await screen.findByRole("button", { name: "추천 생성" });
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("생성 버튼을 누르면 X-Device-Token과 함께 추천을 요청한다", async () => {
    render(<RecommendClient />);

    fireEvent.click(await screen.findByRole("button", { name: "추천 생성" }));

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

    expect(global.fetch).toHaveBeenCalledWith(
      "/api/v1/numbers/recommend",
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({ "X-Device-Token": "a".repeat(64) }),
        body: JSON.stringify({
          count: 5,
          excludedNumbers: [],
          lockedNumbers: [],
          strategy: "random",
        }),
      }),
    );
  });

  it("생성 버튼을 누르면 반환된 추천을 렌더링한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse({
        recommendations: [[4, 8, 12, 16, 20, 24]],
        strategy: "random",
        algorithmVersion: "uniform-random-v1",
        historyThroughRound: 1189,
        setId: null,
        items: [{ position: 1, numbers: [4, 8, 12, 16, 20, 24], score: null, explanationCodes: [] }],
        createdAt: null,
      }),
    );

    render(<RecommendClient />);
    fireEvent.click(await screen.findByRole("button", { name: "추천 생성" }));

    await waitFor(() => {
      [4, 8, 12, 16, 20, 24].forEach((n) => expect(screen.getByText(String(n))).toBeInTheDocument());
    });
  });

  it("생성 요청이 응답 오류로 실패하면 서버 메시지를 보여준다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ message: "temporary outage" }, false, 503));

    render(<RecommendClient />);
    fireEvent.click(await screen.findByRole("button", { name: "추천 생성" }));

    await waitFor(() => {
      expect(screen.getByRole("status")).toHaveTextContent("temporary outage");
    });
  });

  it("전략을 바꾸고 번호를 고정한 뒤 생성하면 요청에 반영된다", async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse(EMPTY_RESULT));

    render(<RecommendClient />);
    await screen.findByRole("button", { name: "추천 생성" });

    fireEvent.click(screen.getByRole("radio", { name: "균형 조합" }));
    fireEvent.click(screen.getByRole("button", { name: "번호 3" }));
    fireEvent.click(screen.getByRole("button", { name: "추천 생성" }));

    await waitFor(() => expect(global.fetch).toHaveBeenCalledTimes(1));

    expect(global.fetch).toHaveBeenLastCalledWith(
      "/api/v1/numbers/recommend",
      expect.objectContaining({
        body: JSON.stringify({
          count: 5,
          excludedNumbers: [],
          lockedNumbers: [3],
          strategy: "balanced",
        }),
      }),
    );
  });

  it("번호를 세 번 누르면 고정 → 제외 → 해제 순으로 순환한다", async () => {
    render(<RecommendClient />);
    await screen.findByRole("button", { name: "추천 생성" });

    const cell = screen.getByRole("button", { name: "번호 3" });
    fireEvent.click(cell);
    expect(screen.getByRole("button", { name: "번호 3, 고정" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "번호 3, 고정" }));
    expect(screen.getByRole("button", { name: "번호 3, 제외" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "번호 3, 제외" }));
    expect(screen.getByRole("button", { name: "번호 3" })).toBeInTheDocument();
  });

  it("확률 고지 문구를 표시한다", async () => {
    render(<RecommendClient />);
    await screen.findByRole("button", { name: "추천 생성" });

    expect(
      screen.getByText(
        "모든 유효한 로또 6/45 조합의 1등 당첨 확률은 동일합니다. 추천 전략은 번호를 선택하는 방식을 설명할 뿐 당첨 확률을 높이지 않습니다.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("역대 1등 당첨 번호 조합은 모든 추천 전략에서 항상 제외됩니다.")).toBeInTheDocument();
  });

  it("응답에 세트 정보가 있으면 전략·알고리즘 버전·반영 회차를 표시한다", async () => {
    global.fetch = vi.fn().mockResolvedValue(
      jsonResponse({
        recommendations: [[1, 2, 3, 4, 5, 6]],
        strategy: "balanced",
        algorithmVersion: "balanced-v1",
        historyThroughRound: 1189,
        setId: 42,
        items: [{ position: 1, numbers: [1, 2, 3, 4, 5, 6], score: 5, explanationCodes: ["SUM_IN_RANGE"] }],
        createdAt: "2026-07-28T00:00:00Z",
      }),
    );

    render(<RecommendClient />);
    fireEvent.click(await screen.findByRole("button", { name: "추천 생성" }));

    await waitFor(() => {
      expect(screen.getByText(/balanced-v1/)).toBeInTheDocument();
      expect(screen.getByText(/1189회/)).toBeInTheDocument();
      expect(screen.getByText("번호 합계가 일반적인 범위 안에 있어요")).toBeInTheDocument();
    });
  });

  it("추천을 저장한 뒤 저장 완료 상태를 보여준다", async () => {
    global.fetch = vi.fn()
      .mockResolvedValueOnce(
        jsonResponse({
          recommendations: [[1, 7, 15, 23, 38, 45]],
          strategy: "random",
          algorithmVersion: "uniform-random-v1",
          historyThroughRound: 1189,
          setId: null,
          items: [{ position: 1, numbers: [1, 7, 15, 23, 38, 45], score: null, explanationCodes: [] }],
          createdAt: null,
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ created: true }, true, 201));

    render(<RecommendClient />);
    fireEvent.click(await screen.findByRole("button", { name: "추천 생성" }));

    const saveButton = await screen.findByRole("button", { name: "저장" });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "저장됨" })).toBeInTheDocument();
    });
  });
});
