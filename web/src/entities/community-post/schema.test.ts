import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import {
  communityPostSchema,
  isTombstone,
  recommendationAttachmentSchema,
  type CommunityPost,
} from "./schema";

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

const VALID_ATTACHMENT = {
  setId: 1,
  strategy: "balanced",
  algorithmVersion: "v1",
  historyThroughRound: 1150,
  exclusionPolicyVersion: "v1",
  items: [
    {
      position: 1,
      numbers: [1, 2, 3, 4, 5, 6],
      score: null,
      explanationCodes: ["ODD_EVEN_BALANCED"],
    },
  ],
};

describe("추천 첨부 스키마", () => {
  it("정상 첨부를 통과시킨다", () => {
    expect(v.safeParse(recommendationAttachmentSchema, VALID_ATTACHMENT).success).toBe(true);
  });

  it("게시글 스키마에서 첨부가 null이면 통과한다", () => {
    expect(
      v.safeParse(communityPostSchema, { ...VALID, recommendationAttachment: null }).success,
    ).toBe(true);
  });

  it("게시글 스키마에서 정상 첨부도 통과한다", () => {
    expect(
      v.safeParse(communityPostSchema, {
        ...VALID,
        recommendationAttachment: VALID_ATTACHMENT,
      }).success,
    ).toBe(true);
  });

  it("알 수 없는 전략 값을 막는다", () => {
    expect(
      v.safeParse(recommendationAttachmentSchema, { ...VALID_ATTACHMENT, strategy: "NOPE" })
        .success,
    ).toBe(false);
  });

  it("historyThroughRound가 정수가 아니면 막는다", () => {
    expect(
      v.safeParse(recommendationAttachmentSchema, {
        ...VALID_ATTACHMENT,
        historyThroughRound: 1.5,
      }).success,
    ).toBe(false);
  });

  it("번호가 6개가 아닌 항목을 막는다", () => {
    expect(
      v.safeParse(recommendationAttachmentSchema, {
        ...VALID_ATTACHMENT,
        items: [{ ...VALID_ATTACHMENT.items[0], numbers: [1, 2, 3, 4, 5] }],
      }).success,
    ).toBe(false);
  });
});

/** 생성 타입과의 정합성 */
type GeneratedPost = components["schemas"]["CommunityPostResponse"];
const _typesMatch: CommunityPost extends GeneratedPost ? true : never = true;
void _typesMatch;
