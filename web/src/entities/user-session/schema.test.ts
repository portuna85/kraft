import * as v from "valibot";
import { describe, expect, it } from "vitest";

import type { components } from "@/generated/api-types";

import { communitySessionSchema, type CommunitySession } from "./schema";

describe("세션 스키마", () => {
  it("정상 세션 응답을 통과시킨다", () => {
    const VALID = {
      loggedIn: true,
      userId: 1,
      nickname: "닉네임",
      activeProviders: ["kakao"],
    };
    expect(v.safeParse(communitySessionSchema, VALID).success).toBe(true);
  });
});

/** 생성 타입과의 정합성 — M-07(improvement_codex.md), improvement_fe.md §8.4 */
type GeneratedSession = components["schemas"]["CommunitySessionResponse"];
const _typesMatch: CommunitySession extends GeneratedSession ? true : never = true;
void _typesMatch;
