import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PostOwnerActions } from "@/components/community/post-owner-actions";
import { CommunitySessionProvider } from "@/lib/community-session-provider";

const push = vi.fn();
const refresh = vi.fn();
const getCommunitySession = vi.fn();
const deletePost = vi.fn();
const revalidateCommunityPost = vi.fn();
// F-P0-10 이후 CommunitySessionProvider가 로그인 세션을 확인하면 claimDevice()도
// 함께 호출한다 — 이 테스트는 소유자 액션만 검증 대상이라 항상 성공으로 흘려보낸다.
const claimDevice = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, refresh }),
  // KF-05: CommunitySessionProvider가 usePathname으로 세션 스코프를 판단한다 — 이 컴포넌트는
  // 항상 게시글 상세(/community/posts/:id) 하위에서만 쓰이므로 스코프 안 경로로 고정한다.
  usePathname: () => "/community/posts/1",
}));

vi.mock("@/lib/community-client", () => ({
  getCommunitySession: () => getCommunitySession(),
  deletePost: (postId: number, version: number) => deletePost(postId, version),
  claimDevice: () => claimDevice(),
}));

vi.mock("@/lib/community-revalidate", () => ({
  revalidateCommunityPost: (postId: number) => revalidateCommunityPost(postId),
}));

function renderOwnerActions(props: { postId: number; ownerId: number; version: number }) {
  return render(
    <CommunitySessionProvider>
      <PostOwnerActions {...props} />
    </CommunitySessionProvider>
  );
}

describe("커뮤니티 게시글 소유자 액션", () => {
  beforeEach(() => {
    push.mockClear();
    refresh.mockClear();
    getCommunitySession.mockReset();
    deletePost.mockReset();
    revalidateCommunityPost.mockReset();
    claimDevice.mockReset().mockResolvedValue({
      mergedSavedNumberCount: 0,
      duplicateSavedNumberCount: 0,
      mergedRecommendationSetCount: 0,
    });
    window.sessionStorage.clear();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("로그인하지 않은 사용자에게는 아무 것도 보이지 않는다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: false, userId: null, nickname: null });

    const { container } = renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });

    await waitFor(() => expect(getCommunitySession).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("로그인했지만 소유자가 아니면 아무 것도 보이지 않는다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 999, nickname: "다른사람" });

    const { container } = renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });

    await waitFor(() => expect(getCommunitySession).toHaveBeenCalled());
    expect(container).toBeEmptyDOMElement();
  });

  it("소유자에게는 수정 링크와 삭제 버튼이 보인다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 10, nickname: "글쓴이" });

    renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });

    expect(await screen.findByRole("link", { name: "수정" })).toHaveAttribute(
      "href",
      "/community/posts/1/edit"
    );
    expect(screen.getByRole("button", { name: "삭제" })).toBeInTheDocument();
  });

  it("삭제 확인을 취소하면 삭제 요청을 보내지 않는다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 10, nickname: "글쓴이" });
    vi.spyOn(window, "confirm").mockReturnValue(false);

    renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });
    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));

    expect(deletePost).not.toHaveBeenCalled();
  });

  it("삭제를 확인하면 게시글을 삭제하고 캐시를 무효화한 뒤 목록으로 이동한다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 10, nickname: "글쓴이" });
    deletePost.mockResolvedValue(undefined);

    renderOwnerActions({ postId: 1, ownerId: 10, version: 2 });
    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(deletePost).toHaveBeenCalledWith(1, 2));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/community"));
    expect(revalidateCommunityPost).toHaveBeenCalledWith(1);
    expect(refresh).toHaveBeenCalled();
  });

  it("삭제 중 충돌이 나면 에러 메시지를 보여주고 버튼을 다시 활성화한다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 10, nickname: "글쓴이" });
    deletePost.mockRejectedValue(new Error("version conflict"));

    renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });
    const deleteButton = await screen.findByRole("button", { name: "삭제" });
    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(screen.getByText(/다른 곳에서 먼저 수정·삭제되었습니다/)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "삭제" })).not.toBeDisabled();
  });

  it("삭제 진행 중에는 버튼이 비활성화되어 중복 클릭을 막는다", async () => {
    getCommunitySession.mockResolvedValue({ loggedIn: true, userId: 10, nickname: "글쓴이" });
    let resolveDelete: (() => void) | undefined;
    deletePost.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveDelete = resolve;
        })
    );

    renderOwnerActions({ postId: 1, ownerId: 10, version: 0 });
    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));

    await waitFor(() => expect(screen.getByRole("button", { name: "삭제 중…" })).toBeDisabled());

    resolveDelete?.();
    await waitFor(() => expect(push).toHaveBeenCalled());
  });
});
