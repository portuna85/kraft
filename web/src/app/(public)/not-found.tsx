import { NotFoundContent } from "@/shared/ui/not-found-content";

/**
 * KF-16(docs/improvement.md): 루트 `not-found.tsx`는 자체 `<main id="main">`을
 * 반환한다. `(public)/**` 하위에 이 파일이 없으면, 매치된 라우트 안에서
 * `notFound()`가 호출될 때(예: `info/[slug]/page.tsx:39`) 이미 마운트된
 * `(public)/layout.tsx`의 `<main id="main">` 안에 루트 not-found가 다시
 * 렌더돼 main 랜드마크가 중첩되고 id가 중복됐다. 공개 레이아웃이 이미 main을
 * 제공하므로 여기서는 새로 만들지 않는다 — 완전히 매치되지 않는 경로는 이
 * 파일을 거치지 않고 그대로 루트 not-found.tsx를 탄다.
 */
export default function PublicNotFound() {
  return <NotFoundContent />;
}
