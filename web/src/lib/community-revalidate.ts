"use server";

import { revalidateTag } from "next/cache";

// 커뮤니티 쓰기(작성/수정/삭제) 성공 직후 클라이언트 컴포넌트에서 직접 호출한다.
// Server Action은 Next가 same-origin 호출만 허용하도록 보호하므로, 당첨번호 전용인
// /api/revalidate(시크릿 필요)와 달리 별도 인증 없이 안전하게 노출할 수 있다.
export async function revalidateCommunityPost(postId: number): Promise<void> {
  // profile 인자는 'use cache' 프로필 캐시에만 의미가 있다. 이 코드베이스는 classic
  // fetch(next:{revalidate,tags}) 패턴만 쓰므로 "무조건 즉시 무효화"를 뜻하는 'max'로 고정한다.
  revalidateTag("community:posts", "max");
  revalidateTag(`community:post:${postId}`, "max");
}
