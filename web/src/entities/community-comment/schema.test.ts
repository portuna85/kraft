import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import {
  commentPageSchema,
  communityCommentSchema,
  isCommentSubmittable,
  type CommentPage,
  type CommunityComment,
} from "./schema";

function comment(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 1,
    postId: 10,
    parentId: null,
    ownerId: 5,
    authorNickname: "나",
    content: "내용",
    deleted: false,
    createdAt: "2026-08-01T12:00:00Z",
    targetPage: null,
    replies: [],
    ...overrides,
  };
}

describe("댓글 스키마", () => {
  it("정상 댓글을 통과시킨다", () => {
    expect(v.safeParse(communityCommentSchema, comment()).success).toBe(true);
  });

  it("답글을 중첩해서 통과시킨다", () => {
    const nested = comment({ replies: [comment({ id: 2, parentId: 1 })] });
    const result = v.safeParse(communityCommentSchema, nested);
    expect(result.success).toBe(true);
  });

  it("삭제된 댓글은 ownerId가 없어도 통과한다", () => {
    expect(
      v.safeParse(communityCommentSchema, comment({ deleted: true, ownerId: null })).success,
    ).toBe(true);
  });
});

describe("댓글 페이지 스키마", () => {
  it("상위 댓글 목록과 집계를 통과시킨다", () => {
    const page = {
      topLevel: [comment()],
      totalTopLevelComments: 1,
      page: 0,
      size: 20,
      totalPages: 1,
    };
    expect(v.safeParse(commentPageSchema, page).success).toBe(true);
  });
});

describe("댓글 제출 가능 여부", () => {
  it("빈 내용이나 공백만 있으면 제출 불가다", () => {
    expect(isCommentSubmittable("")).toBe(false);
    expect(isCommentSubmittable("   ")).toBe(false);
  });

  it("1000자를 넘으면 제출 불가다", () => {
    expect(isCommentSubmittable("a".repeat(1001))).toBe(false);
  });

  it("정상 범위 내용은 제출 가능하다", () => {
    expect(isCommentSubmittable("정상 댓글")).toBe(true);
    expect(isCommentSubmittable("a".repeat(1000))).toBe(true);
  });
});

/** 생성 타입과의 정합성 — M-07*/
type GeneratedComment = components["schemas"]["CommunityCommentResponse"];
const _commentTypesMatch: CommunityComment extends GeneratedComment ? true : never = true;
void _commentTypesMatch;

type GeneratedCommentPage = components["schemas"]["CommunityCommentPageResponse"];
const _pageTypesMatch: CommentPage extends GeneratedCommentPage ? true : never = true;
void _pageTypesMatch;
