import * as v from "valibot";

import { browserMutate, browserQuery } from "@/shared/api/transport";

import { communityPostSchema, POST_CATEGORIES, type CommunityPost } from "./schema";

/**
 * 작성·수정 계약 — improvement_fe.md §23.11, §25.6
 *
 * 길이 제한은 백엔드 @Size와 같아야 한다. 화면이 먼저 막아야 사용자가 400 대신
 * "200자까지 쓸 수 있습니다"를 본다.
 */
export const TITLE_MAX_LENGTH = 200;
export const CONTENT_MAX_LENGTH = 20_000;

export const postFormSchema = v.object({
  title: v.pipe(
    v.string(),
    v.trim(),
    v.minLength(1, "제목을 입력해 주세요."),
    v.maxLength(TITLE_MAX_LENGTH, `제목은 ${TITLE_MAX_LENGTH}자까지 쓸 수 있습니다.`),
  ),
  content: v.pipe(
    v.string(),
    v.trim(),
    v.minLength(1, "내용을 입력해 주세요."),
    v.maxLength(CONTENT_MAX_LENGTH, `내용은 ${CONTENT_MAX_LENGTH}자까지 쓸 수 있습니다.`),
  ),
  category: v.picklist(POST_CATEGORIES, "분류를 선택해 주세요."),
});

export type PostFormValues = v.InferOutput<typeof postFormSchema>;

/**
 * 글 작성. 추천 세트를 첨부하면 **디바이스 토큰이 필요하다.**
 *
 * 백엔드가 세트의 client_token_hash와 대조해 소유를 확인하기 때문이다. 평소에는
 * 로그인 요청에 디바이스 토큰을 붙이지 않지만(§29.6), 여기서는 그 토큰이 인증 수단이
 * 아니라 "이 기기에서 만든 세트가 맞다"는 증거로 쓰인다. 첨부가 없으면 붙이지 않는다.
 */
export function createPost(
  values: PostFormValues,
  recommendationSetId: number | null,
): Promise<CommunityPost> {
  return browserMutate("/api/v1/community/posts", communityPostSchema, {
    method: "POST",
    deviceScoped: recommendationSetId !== null,
    body: { ...values, recommendationSetId },
  });
}

/**
 * 글 수정. `expectedVersion`은 **본문에** 실린다(삭제는 쿼리 파라미터라 서로 다르다).
 * 버전이 어긋나면 백엔드가 409를 내고, 호출부는 최신을 다시 읽어 사용자에게 보여준다.
 */
export function updatePost(
  postId: number,
  values: PostFormValues,
  expectedVersion: number,
): Promise<CommunityPost> {
  return browserMutate(`/api/v1/community/posts/${postId}`, communityPostSchema, {
    method: "PUT",
    body: { title: values.title, content: values.content, expectedVersion },
  });
}

/**
 * 최신 게시글을 **ISR을 우회해** 읽는다 — improvement_fe.md §5.3, 레거시 FE-066.
 *
 * 409 충돌 뒤에 쓰는 함수다. 서버 컴포넌트를 다시 태우면 캐시된 값을 받을 수 있어
 * "최신"이라며 보여준 내용이 또 옛 것일 수 있다. 브라우저에서 직접 물어본다.
 */
export function fetchLatestPost(postId: number): Promise<CommunityPost> {
  return browserQuery(`/api/v1/community/posts/${postId}`, communityPostSchema);
}
