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
    // 최신 회차 수집 직후에는 첫 방문자도 새 값을 봐야 한다. "max" 프로필은 태그를
    // stale로만 표시해 다음 요청에 옛 값을 한 번 서빙한 뒤 백그라운드 갱신하므로 쓰지
    // 않는다. Route Handler에서는 updateTag를 호출할 수 없어 expire: 0 프로필로 즉시
    // 만료한다. 다음 요청은 캐시 미스로 처리돼 백엔드의 새 값을 기다린다.
    revalidateTag(tag, { expire: 0 });
  }
  for (const path of paths) {
    revalidatePath(path);
  }

  return Response.json({ revalidated: true, paths, tags });
}
