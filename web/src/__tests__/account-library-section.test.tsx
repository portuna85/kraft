import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
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

function renderSection() {
  return render(
    <CommunitySessionProvider>
      <AccountLibrarySection />
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
