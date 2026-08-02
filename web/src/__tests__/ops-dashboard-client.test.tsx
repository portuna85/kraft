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

/**
 * FE-079: callOps가 content-type을 보고 파싱 여부를 정하므로, 실제 Response처럼
 * headers를 갖춘 double을 쓴다. contentType을 넘기면 JSON이 아닌 응답도 흉내 낼 수 있다.
 */
function mockFetch(
  handler: (url: string, init?: RequestInit) => { status: number; body: unknown; contentType?: string }
) {
  return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
    const { status, body, contentType = "application/json" } = handler(url, init);
    return Promise.resolve({
      ok: status >= 200 && status < 300,
      status,
      headers: new Headers(contentType ? { "content-type": contentType } : {}),
      json: () => Promise.resolve(body),
      text: () => Promise.resolve(typeof body === "string" ? body : JSON.stringify(body)),
    });
  });
}

/** 수동 적재 폼을 서버까지 도달하는 유효한 값으로 채운다. */
function fillValidManualEntry() {
  fireEvent.change(screen.getByLabelText("회차"), { target: { value: "1230" } });
  fireEvent.change(screen.getByLabelText("추첨일"), { target: { value: "2026-01-03" } });
  fireEvent.change(screen.getByPlaceholderText("예: 3, 11, 19, 28, 34, 42"), {
    target: { value: "1, 2, 3, 4, 5, 6" },
  });
  fireEvent.change(screen.getByPlaceholderText("예: 7"), { target: { value: "7" } });
  fireEvent.change(screen.getByPlaceholderText("예: 2100000000"), { target: { value: "2100000000" } });
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
        headers: new Headers({ "content-type": "application/json" }),
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
    fireEvent.change(screen.getByLabelText("추첨일"), { target: { value: "2026-01-03" } });
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
      drawDate: "2026-01-03",
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
    fillValidManualEntry();
    fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

    await waitFor(() => {
      expect(screen.getByText("이미 존재하는 회차입니다.")).toBeInTheDocument();
    });
    expect(screen.getByLabelText("회차")).toHaveValue(1230);
  });

  // FE-077: 작업 성공 후 자동 갱신이 실패해도 else가 없어 아무 표시가 없었다.
  // 사용자는 화면의 옛 요약을 최신 상태로 오인한다.
  it("작업 후 상태 갱신에 실패하면 표시된 값이 옛 것임을 알린다", async () => {
    let summaryCalls = 0;
    global.fetch = mockFetch((url) => {
      if (url === "/ops-api/summary") {
        summaryCalls += 1;
        // 첫 조회는 성공, 작업 후 자동 갱신은 실패시킨다.
        return summaryCalls === 1
          ? { status: 200, body: SUMMARY }
          : { status: 500, body: { message: "일시적 오류" } };
      }
      if (url === "/ops-api/collect/latest") return { status: 200, body: COLLECTED };
      throw new Error(`예상치 못한 요청: ${url}`);
    });

    render(<OpsDashboardClient />);
    fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
      target: { value: "secret-token" },
    });
    fireEvent.click(screen.getByRole("button", { name: "운영 상태 확인" }));
    await waitFor(() => expect(screen.getByText("운영 상태를 불러왔습니다.")).toBeInTheDocument());

    fireEvent.click(screen.getByRole("button", { name: "최신 회차 반영" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("운영 상태를 갱신하지 못했습니다");
    });
    // 작업 자체는 성공했다는 사실을 덮지 않는다.
    expect(screen.getByText("최신 회차 데이터를 반영했습니다.")).toBeInTheDocument();
  });

  // FE-074: 이전에는 검증이 전혀 없어 빈 회차가 Number("")===0으로, 번호 3개가 그대로 서버에 갔다.
  // 아래 테스트들의 핵심 단언은 "요청이 아예 나가지 않는다"이다.
  describe("수동 적재 입력 검증", () => {
    // 빈 값은 브라우저 기본 검증(required)이 먼저 막는다 — 예전에는 required조차 없어
    // Number("")===0이 서버로 갔다. 여기서 확인할 것은 "요청이 나가지 않는다"이다.
    it("빈 폼을 제출하면 요청을 보내지 않는다", () => {
      const fetchMock = mockFetch(() => ({ status: 200, body: {} }));
      global.fetch = fetchMock;

      render(<OpsDashboardClient />);
      fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
        target: { value: "secret-token" },
      });
      fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

      expect(fetchMock).not.toHaveBeenCalled();
      expect(screen.getByLabelText("회차")).toBeRequired();
      expect(screen.getByLabelText("추첨일")).toBeRequired();
    });

    // 개수·중복·범위는 required로 표현할 수 없어 JS 검증이 담당한다.
    it("회차가 0이면 요청을 보내지 않고 오류를 알린다", () => {
      const fetchMock = mockFetch(() => ({ status: 200, body: {} }));
      global.fetch = fetchMock;

      render(<OpsDashboardClient />);
      fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
        target: { value: "secret-token" },
      });
      fillValidManualEntry();
      fireEvent.change(screen.getByLabelText("회차"), { target: { value: "0" } });
      fireEvent.submit(screen.getByRole("button", { name: "수동 등록" }).closest("form")!);

      expect(fetchMock).not.toHaveBeenCalled();
      expect(screen.getByRole("alert")).toHaveTextContent("회차는 1 이상의 정수여야 합니다.");
      expect(screen.getByLabelText("회차")).toHaveAttribute("aria-invalid", "true");
    });

    it("번호를 6개 미만으로 적으면 요청을 보내지 않는다", () => {
      const fetchMock = mockFetch(() => ({ status: 200, body: {} }));
      global.fetch = fetchMock;

      render(<OpsDashboardClient />);
      fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
        target: { value: "secret-token" },
      });
      fillValidManualEntry();
      fireEvent.change(screen.getByPlaceholderText("예: 3, 11, 19, 28, 34, 42"), {
        target: { value: "1, 2, 3" },
      });
      fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

      expect(fetchMock).not.toHaveBeenCalled();
      expect(screen.getByRole("alert")).toHaveTextContent("번호는 6개여야 합니다");
    });

    it("보너스 번호가 당첨 번호와 겹치면 요청을 보내지 않는다", () => {
      const fetchMock = mockFetch(() => ({ status: 200, body: {} }));
      global.fetch = fetchMock;

      render(<OpsDashboardClient />);
      fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
        target: { value: "secret-token" },
      });
      fillValidManualEntry();
      fireEvent.change(screen.getByPlaceholderText("예: 7"), { target: { value: "6" } });
      fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

      expect(fetchMock).not.toHaveBeenCalled();
      expect(screen.getByRole("alert")).toHaveTextContent("당첨 번호 6개와 달라야 합니다");
    });

    it("번호에 중복이 있으면 요청을 보내지 않는다", () => {
      const fetchMock = mockFetch(() => ({ status: 200, body: {} }));
      global.fetch = fetchMock;

      render(<OpsDashboardClient />);
      fireEvent.change(screen.getByPlaceholderText("X-Ops-Token 값을 입력하세요"), {
        target: { value: "secret-token" },
      });
      fillValidManualEntry();
      fireEvent.change(screen.getByPlaceholderText("예: 3, 11, 19, 28, 34, 42"), {
        target: { value: "1, 1, 3, 4, 5, 6" },
      });
      fireEvent.click(screen.getByRole("button", { name: "수동 등록" }));

      expect(fetchMock).not.toHaveBeenCalled();
      expect(screen.getByRole("alert")).toHaveTextContent("중복");
    });
  });
});
