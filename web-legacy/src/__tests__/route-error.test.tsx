import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import { RouteError } from "@/components/route-error";
import CommunityError from "@/app/community/error";

// M-3: error.tsx는 클라이언트 컴포넌트라 서버 pino 파이프라인에 직접 로그를 남길 수
// 없다 — sendBeacon으로 /api/client-error에 보고해야 서버 로그(운영 관측)에 남는다.
// console.error만으로는(개선 전 상태) 브라우저 콘솔에만 남고 서버는 조용하다.
describe("RouteError", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("sendBeacon으로 /api/client-error에 message·digest·route를 보고한다", () => {
    const sendBeacon = vi.fn().mockReturnValue(true);
    vi.stubGlobal("navigator", { sendBeacon });
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = Object.assign(new Error("데이터를 가져오지 못했습니다"), { digest: "abc123" });

    render(<RouteError error={error} reset={vi.fn()} />);

    expect(sendBeacon).toHaveBeenCalledTimes(1);
    // 바로 위에서 1회 호출을 확인했으므로 calls[0]은 항상 존재한다
    // (noUncheckedIndexedAccess가 인덱싱 결과를 `| undefined`로 좁히는 것뿐).
    const [url, payload] = sendBeacon.mock.calls[0]!;
    expect(url).toBe("/api/client-error");
    const body = JSON.parse(payload as string);
    expect(body.message).toBe("데이터를 가져오지 못했습니다");
    expect(body.digest).toBe("abc123");
    expect(body.route).toBe(window.location.pathname);
  });

  it("sendBeacon이 없으면 fetch로 폴백한다", () => {
    vi.stubGlobal("navigator", {});
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("실패");

    render(<RouteError error={error} reset={vi.fn()} />);

    expect(fetchMock).toHaveBeenCalledWith(
      "/api/client-error",
      expect.objectContaining({ method: "POST", keepalive: true })
    );
  });
});

describe("커뮤니티 세그먼트 오류 경계", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("getCommunityPosts 실패를 이 경계가 잡아 재시도·홈 이동 액션을 보여준다", () => {
    vi.stubGlobal("navigator", { sendBeacon: vi.fn().mockReturnValue(true) });
    vi.spyOn(console, "error").mockImplementation(() => {});
    const error = new Error("커뮤니티 게시글을 불러오지 못했습니다");

    render(<CommunityError error={error} reset={vi.fn()} />);

    expect(screen.getByText("페이지를 불러오지 못했습니다")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "다시 불러오기" })).toBeInTheDocument();
  });
});
