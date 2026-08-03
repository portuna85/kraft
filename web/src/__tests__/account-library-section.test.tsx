import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { AccountLibrarySection } from "@/features/identity/account-library-section";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

vi.mock("next/navigation", () => ({
  usePathname: () => "/saved",
}));

const SESSION = { loggedIn: true, userId: 1, nickname: "나", activeProviders: ["google"] };

const SAVED = [
  { id: 1, numbers: [1, 2, 3, 4, 5, 6], label: "행운 번호", source: "MANUAL", createdAt: "2026-01-03T12:00:00Z" },
];

const SETS = {
  items: [
    {
      id: 5,
      strategy: "balanced",
      algorithmVersion: "v1",
      historyThroughRound: 1230,
      exclusionPolicyVersion: "v1",
      lockedNumbers: [],
      excludedNumbers: [],
      createdAt: "2026-01-04T09:00:00Z",
      items: [{ position: 1, numbers: [7, 8, 9, 10, 11, 12], explanationCodes: [] }],
    },
  ],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
};

function mockFetch(options: { saved?: unknown; sets?: unknown; fail?: boolean }) {
  return vi.fn().mockImplementation((url: string) => {
    if (url.includes("/session")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(SESSION) });
    }
    if (options.fail) return Promise.reject(new Error("network down"));
    if (url.includes("saved-numbers")) {
      return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(options.saved ?? []) });
    }
    return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(options.sets ?? { ...SETS, items: [] }) });
  });
}

function renderSection(latestRound = 0) {
  return render(
    <CommunitySessionProvider>
      <AccountLibrarySection latestRound={latestRound} />
    </CommunitySessionProvider>
  );
}

// FE-040: 로딩 중과 "기록 없음"이 모두 null 렌더라 섹션이 나타났다 사라지거나 존재를 몰랐다.
describe("계정 보관함 섹션", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("기록이 없어도 사라지지 않고 빈 상태를 설명한다", async () => {
    global.fetch = mockFetch({ saved: [], sets: { ...SETS, items: [] } });

    renderSection();

    expect(await screen.findByText(/아직 계정에 연결된 기록이 없습니다/)).toBeInTheDocument();
  });

  it("저장 번호에 라벨과 저장 시각을 함께 보여준다", async () => {
    global.fetch = mockFetch({ saved: SAVED, sets: { ...SETS, items: [] } });

    renderSection();

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "저장 번호" })).toBeInTheDocument();
    });
    expect(screen.getByText(/행운 번호/)).toBeInTheDocument();
    // 번호만 나열하던 이전 동작에서는 시각 정보가 전혀 없었다.
    expect(document.querySelector("time[datetime='2026-01-03T12:00:00Z']")).not.toBeNull();
  });

  it("조회에 실패하면 재시도를 제공한다", async () => {
    global.fetch = mockFetch({ fail: true });

    renderSection();

    expect(await screen.findByText("계정 보관함을 불러오지 못했습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  });
});

// C-2: 계정 스코프로도 SavedNumbersClient/RecommendationHistoryClient와 동등한
// 삭제·페이지네이션 기능을 제공한다 — 예전에는 목록 표시만 가능했다.
describe("계정 보관함 섹션의 관리 기능(C-2)", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  function mockFetchWithHandlers(handler: (url: string, init?: RequestInit) => { status: number; body: unknown }) {
    return vi.fn().mockImplementation((url: string, init?: RequestInit) => {
      const { status, body } = handler(url, init);
      return Promise.resolve({ ok: status >= 200 && status < 300, status, json: () => Promise.resolve(body) });
    });
  }

  it("저장 번호를 삭제하면 계정 스코프 엔드포인트로 요청하고 목록에서 지운다", async () => {
    let deleteUrl: string | null = null;
    global.fetch = mockFetchWithHandlers((url, init) => {
      if (url.includes("/session")) return { status: 200, body: SESSION };
      if (init?.method === "DELETE") {
        deleteUrl = url;
        return { status: 204, body: null };
      }
      if (url.includes("/matches")) return { status: 200, body: [] };
      if (url.includes("saved-numbers")) return { status: 200, body: SAVED };
      return { status: 200, body: { ...SETS, items: [] } };
    });

    renderSection();
    fireEvent.click(await screen.findByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(deleteUrl).toBe("/api/v1/community/me/saved-numbers/1");
    });
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: "1, 2, 3, 4, 5, 6 조합 삭제" })).not.toBeInTheDocument();
    });
  });

  it("추천 이력을 삭제하면 계정 스코프 엔드포인트로 요청하고 목록에서 지운다", async () => {
    let deleteUrl: string | null = null;
    global.fetch = mockFetchWithHandlers((url, init) => {
      if (url.includes("/session")) return { status: 200, body: SESSION };
      if (init?.method === "DELETE") {
        deleteUrl = url;
        return { status: 204, body: null };
      }
      if (url.includes("saved-numbers")) return { status: 200, body: [] };
      return { status: 200, body: SETS };
    });

    renderSection();
    await waitFor(() => expect(screen.getByRole("heading", { name: "추천 이력" })).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: /추천 세트 삭제/ }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));

    await waitFor(() => {
      expect(deleteUrl).toBe("/api/v1/community/me/recommendation-sets/5");
    });
    await waitFor(() => {
      expect(screen.queryByRole("button", { name: /추천 세트 삭제/ })).not.toBeInTheDocument();
    });
  });

  it("추천 이력이 더 있으면 더 보기로 다음 페이지를 가져온다", async () => {
    const firstPage = { ...SETS, page: 0, totalPages: 2, totalElements: 2 };
    const secondItem = {
      ...SETS.items[0],
      id: 6,
      createdAt: "2026-01-05T09:00:00Z",
    };
    global.fetch = mockFetchWithHandlers((url) => {
      if (url.includes("/session")) return { status: 200, body: SESSION };
      if (url.includes("saved-numbers")) return { status: 200, body: [] };
      if (url.includes("page=1")) {
        return { status: 200, body: { items: [secondItem], page: 1, size: 20, totalElements: 2, totalPages: 2 } };
      }
      return { status: 200, body: firstPage };
    });

    renderSection();
    await waitFor(() => expect(screen.getByText(/전체 2건 중 1건 표시/)).toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "더 보기" }));

    await waitFor(() => {
      expect(screen.getByText(/전체 2건을 모두 표시했습니다/)).toBeInTheDocument();
    });
    expect(screen.getAllByRole("button", { name: /추천 세트 삭제/ })).toHaveLength(2);
  });
});
