import { cache } from "react";

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
 *
 * KF-26(FE-OPT-23, docs/improvement.md): 게시글 상세 라우트가 `generateMetadata`와
 * 페이지 본문에서 각각 이 함수를 부른다 — 조회수 부수효과가 있어 중복 호출이면
 * 방문 1회당 조회수가 2 올라간다. React `cache()`로 감싸 같은 요청 생명주기 안의
 * 두 호출을 하나로 합친다. `cache()`는 요청 단위로만 유효해 다른 요청·다른
 * 라우트(예: 수정 화면)의 호출에는 영향이 없다.
 */
export const getPost = cache((id: number): Promise<CommunityPost> => {
  return serverFetch(
    `${serverEnv.backendInternalUrl}/api/v1/community/posts/${id}`,
    communityPostSchema,
    { cache: { mode: "no-store" } },
  );
});
