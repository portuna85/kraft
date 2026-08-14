import { serverFetch } from "@/shared/api/transport";
import { CACHE_TAGS } from "@/shared/config/cache-tags";
import { serverEnv } from "@/shared/config/env";

import type { ListParams } from "./query";
import {
  communityPostPageSchema,
  communityPostSchema,
  type CommunityPost,
  type CommunityPostPage,
} from "./schema";

/**
 * 커뮤니티 API 바인딩
 */
export const COMMUNITY_POSTS_TAG = CACHE_TAGS.communityPosts;
export const REVALIDATE_COMMUNITY_LIST_SECONDS = 30;
export const DEFAULT_PAGE_SIZE = 20;

export function getPostPage(params: ListParams): Promise<CommunityPostPage> {
  const search = new URLSearchParams({
    page: String(params.page),
    size: String(DEFAULT_PAGE_SIZE),
    sort: params.sort,
  });
  if (params.category !== undefined) search.set("category", params.category);
  if (params.query !== undefined) search.set("query", params.query);

  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/community/posts?${search.toString()}`,
    communityPostPageSchema,
    {
      cache: {
        mode: "revalidate",
        seconds: REVALIDATE_COMMUNITY_LIST_SECONDS,
        tags: [COMMUNITY_POSTS_TAG],
      },
    },
  );
}

/**
 * 게시글 상세는 **캐시하지 않는다** — 레거시 M-4/FE-064.
 *
 * 이 GET은 백엔드에서 조회수를 증가시키는 부수효과를 갖는다. ISR로 캐시하면 두 번째
 * 방문부터 백엔드에 요청이 가지 않아 조회수가 조용히 멈춘다. 지표가 망가진 것은
 * 화면상으로는 아무 문제가 없어 보여서 늦게 발견된다 — 그래서 성능을 이유로 이
 * 설정을 바꾸려는 시도를 막으려고 이유를 여기 남긴다(R-4).
 */
export function getPost(id: number): Promise<CommunityPost> {
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/community/posts/${id}`,
    communityPostSchema,
    { cache: { mode: "no-store" } },
  );
}
