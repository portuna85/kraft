import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import { communityPostSchema, isTombstone, type CommunityPost } from "./schema";

const VALID = {
  id: 1,
  ownerId: 10,
  authorNickname: "테스터",
  title: "제목",
  content: "본문",
  category: "GENERAL",
  status: "PUBLISHED",
  version: 0,
  createdAt: "2026-08-01T12:00:00Z",
  updatedAt: "2026-08-01T12:00:00Z",
  likeCount: 0,
  commentCount: 0,
  viewCount: 0,
  recommendationAttachment: null,
};

describe("게시글 스키마", () => {
  it("정상 응답을 통과시킨다", () => {
    expect(v.safeParse(communityPostSchema, VALID).success).toBe(true);
  });

  it("알 수 없는 분류·상태 값을 막는다", () => {
    expect(v.safeParse(communityPostSchema, { ...VALID, category: "NOPE" }).success).toBe(false);
    expect(v.safeParse(communityPostSchema, { ...VALID, status: "NOPE" }).success).toBe(false);
  });
});

describe("tombstone 판정", () => {
  it("PUBLISHED가 아니면 tombstone이다", () => {
    expect(isTombstone({ ...VALID, status: "PUBLISHED" } as CommunityPost)).toBe(false);
    expect(isTombstone({ ...VALID, status: "DELETED" } as CommunityPost)).toBe(true);
    expect(isTombstone({ ...VALID, status: "HIDDEN_BY_AUTHOR" } as CommunityPost)).toBe(true);
  });
});

/**
 * 생성 타입과의 정합성 — improvement_fe.md §8.4
 *
 * recommendationAttachment는 아직 첨부 렌더링이 구현되지 않아 의도적으로
 * `v.unknown()`으로 남겨 뒀다 — unknown은 어떤 구체 타입의 부분집합도 아니라서
 * 그 필드까지 비교하면 항상 실패한다. 나머지 필드만 대조한다.
 */
type GeneratedPost = Omit<
  components["schemas"]["CommunityPostResponse"],
  "recommendationAttachment"
>;
type CheckedPost = Omit<CommunityPost, "recommendationAttachment">;
const _typesMatch: CheckedPost extends GeneratedPost ? true : never = true;
void _typesMatch;
