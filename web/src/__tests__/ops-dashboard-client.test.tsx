import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { OpsDashboardClient } from "@/components/ops-dashboard-client";

const SUMMARY = {
  service: "kraft-lotto",
  timezone: "Asia/Seoul",
  status: "정상",
  latestRound: 1230,
  latestDrawDate: "2026-01-03",
  checkedAt: "2026-01-03T12:00:00Z",
  fresh: true,
};

const COLLECTED = {
  round: 1230,
  drawDate: "2026-01-03",
  numbers: [1, 2, 3, 4, 5, 6],
  bonusNumber: 7,
  firstPrizeAmount: 2_100_000_000,
};

function mockFetch(handler: (url: string, init?: RequestInit) => { status: number; body: unknown }) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const { status, body } = handler(url, init);
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    });
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => { resolve = r; });
  return { promise, resolve };
}

describe("운영 대시보드 화면", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("토큰이 비어 있으면 모든 액션 버튼이 비활성 상태다", () => {
    global.fetch = mockFetch(() => ({ status: 200, body: {} }));

    render(<OpsDashboardClient />);

    expect(screen.getByRole("button", { name: "운영 상태 확인" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "최신 회차 반영" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "지정 회차 반영" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "수동 등록" })).toBeDisabled();
  });

  it("토큰 입력란은 자동완성을 끈 password 입력이다 (F-08: 브라우저 자격 증명 관리자에 노출 금지)", () => {
    render(<OpsDashboardClient />);

    const tokenInput = screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요");
    expect(tokenInput).toHaveAttribute("type", "password");
    expect(tokenInput).toHaveAttribute("autocomplete", "off");
  });

  it("토큰을 입력하면 액션 버튼이 활성화되고, 운영 상태 확인 시 X-Ops-Token 헤더로만 전송한다", async () => {
    global.fetch = mockFetch((url, init) => {
      expect(url).toBe("/ops-api/summary");
      expect(url).not.toContain("secret-token");
      expect((init?.headers as Record<string, string>)["X-Ops-Token"]).toBe("secret-token");
      return { status: 200, body: SUMMARY };
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });

    const summaryButton = screen.getByRole("button", { name: "운영 상태 확인" });
    expect(summaryButton).toBeEnabled();
    fireEvent.click(summaryButton);

    await waitFor(() => {
      expect(screen.getByText("운영 상태를 불러왔습니다.")).toBeInTheDocument();
    });
    expect(screen.getByText("정상")).toBeInTheDocument();
    expect(screen.getByText("1230회")).toBeInTheDocument();
  });

  it("조회 중에는 로딩 문구를 보여주고 버튼이 비활성화된다", async () => {
    const pending = deferred<{ status: number; body: unknown }>();
    global.fetch = vi.fn().mockImplementation(() =>
      pending.promise.then(({ status, body }) => ({
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(body),
      }))
    );

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "운영 상태 확인" }));

    expect(await screen.findByRole("button", { name: "조회 중..." })).toBeDisabled();
    expect(screen.getByRole("button", { name: "최신 회차 반영" })).toBeDisabled();

    pending.resolve({ status: 200, body: SUMMARY });
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "운영 상태 확인" })).toBeEnabled();
    });
  });

  it("토큰이 틀리면 백엔드가 준 오류 메시지를 그대로 보여준다", async () => {
    global.fetch = mockFetch(() => ({ status: 401, body: { message: "운영 토큰이 올바르지 않습니다." } }));

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "wrong-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "운영 상태 확인" }));

    await waitFor(() => {
      expect(screen.getByText("운영 토큰이 올바르지 않습니다.")).toBeInTheDocument();
    });
  });

  it("네트워크 오류(fetch 예외) 시 일반 오류 문구를 보여준다", async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error("network down"));

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "운영 상태 확인" }));

    await waitFor(() => {
      expect(screen.getByText("네트워크 오류가 발생했습니다.")).toBeInTheDocument();
    });
  });

  it("최신 회차 반영 성공 시 결과를 보여주고 운영 상태를 다시 조회한다", async () => {
    let summaryCallCount = 0;
    global.fetch = mockFetch((url) => {
      if (url === "/ops-api/collect/latest") return { status: 200, body: COLLECTED };
      if (url === "/ops-api/summary") { summaryCallCount++; return { status: 200, body: SUMMARY }; }
      throw new Error(`예상치 못한 요청: ${url}`);
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "최신 회차 반영" }));

    await waitFor(() => {
      expect(screen.getByText("최신 회차 데이터를 반영했습니다.")).toBeInTheDocument();
    });
    expect(screen.getByText("1230회차")).toBeInTheDocument();
    expect(summaryCallCount).toBe(1);
  });

  it("지정 회차 반영은 1 이상의 정수가 아니면 요청 없이 검증 메시지만 보여준다", async () => {
    const fetchSpy = vi.fn();
    global.fetch = fetchSpy;

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.change(screen.getByLabelText("특정 회차"), { target: { value: "0" } });
    fireEvent.click(screen.getByRole("button", { name: "지정 회차 반영" }));

    expect(screen.getByText("수집할 회차는 1 이상의 정수여야 합니다.")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("지정 회차 반영에 유효한 회차를 입력하면 해당 회차로 수집 요청을 보낸다", async () => {
    global.fetch = mockFetch((url) => {
      if (url === "/ops-api/collect/500") return { status: 200, body: { ...COLLECTED, round: 500 } };
      if (url === "/ops-api/summary") return { status: 200, body: SUMMARY };
      throw new Error(`예상치 못한 요청: ${url}`);
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.change(screen.getByLabelText("특정 회차"), { target: { value: "500" } });
    fireEvent.click(screen.getByRole("button", { name: "지정 회차 반영" }));

    await waitFor(() => {
      expect(screen.getByText("500회차 데이터를 반영했습니다.")).toBeInTheDocument();
    });
  });

  it("수동 등록은 콤마로 구분한 번호 6개를 파싱해 전송하고, 성공하면 입력값을 초기화한다", async () => {
    let sentBody: unknown = null;
    global.fetch = mockFetch((url, init) => {
      if (url === "/ops-api/rounds") {
        sentBody = JSON.parse(init?.body as string);
        return { status: 200, body: COLLECTED };
      }
      if (url === "/ops-api/summary") return { status: 200, body: SUMMARY };
      throw new Error(`예상치 못한 요청: ${url}`);
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.change(screen.getByLabelText("회차"), { target: { value: "1230" } });
    fireEvent.change(screen.getByPlaceholderText("예: 3, 11, 19, 28, 34, 42"), {
      target: { value: "1, 2, 3, 4, 5, 6" },
    });
    fireEvent.change(screen.getByPlaceholderText("예: 7"), { target: { value: "7" } });
    fireEvent.change(screen.getByPlaceholderText("예: 2100000000"), { target: { value: "2100000000" } });
    fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

    await waitFor(() => {
      expect(screen.getByText("1230회차 데이터를 수동으로 저장했습니다.")).toBeInTheDocument();
    });
    expect(sentBody).toEqual({
      round: 1230,
      drawDate: "",
      numbers: [1, 2, 3, 4, 5, 6],
      bonusNumber: 7,
      firstPrizeAmount: 2100000000,
    });
    // 성공 후 폼이 초기화됐는지 — 회차 입력란이 다시 비어 있어야 한다
    expect(screen.getByLabelText("회차")).toHaveValue(null);
  });

  it("수동 등록 실패 시 오류 메시지를 보여주고 입력값을 유지한다", async () => {
    global.fetch = mockFetch((url) => {
      if (url === "/ops-api/rounds") return { status: 409, body: { message: "이미 존재하는 회차입니다." } };
      throw new Error(`예상치 못한 요청: ${url}`);
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.change(screen.getByLabelText("회차"), { target: { value: "1230" } });
    fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

    await waitFor(() => {
      expect(screen.getByText("이미 존재하는 회차입니다.")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("회차")).toHaveValue(1230);
  });
});
