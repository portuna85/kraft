import { revalidatePath, revalidateTag } from "next/cache";
import { NextRequest } from "next/server";

import { serverEnv } from "@/shared/config/env";

import { filterAllowedPaths, filterAllowedTags, matchesSecret } from "./guard";

/**
 * ISR 웹훅
 *
 * 백엔드 RevalidateWebhookListener가 회차 수집을 커밋한 뒤 이 엔드포인트를 부른다.
 * 검증 로직(화이트리스트·시크릿 비교)은 ./guard.ts에 분리돼 있다 — 여기서는 그 결과로
 * Next 전용 API(revalidateTag/revalidatePath)만 호출한다.
 */
export async function POST(request: NextRequest) {
  const secret = request.headers.get("X-Revalidate-Secret");
  if (!matchesSecret(secret, serverEnv.revalidateSecret)) {
    return new Response("Unauthorized", { status: 401 });
  }

  let body: { paths?: unknown; tags?: unknown };
  try {
    body = (await request.json()) as typeof body;
  } catch {
    return new Response("Invalid JSON body", { status: 400 });
  }

  const paths = filterAllowedPaths(body.paths);
  const tags = filterAllowedTags(body.tags);

  for (const tag of tags) {
    // "max" 프로필 = 무조건 즉시 무효화. 이 코드베이스는 classic
    // fetch(next:{revalidate,tags}) 패턴만 쓰므로 다른 프로필은 의미가 없다.
    revalidateTag(tag, "max");
  }
  for (const path of paths) {
    revalidatePath(path);
  }

  return Response.json({ revalidated: true, paths, tags });
}
