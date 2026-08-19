import { ROUTES } from "@/shared/config/routes";
import { LinkButton } from "@/shared/ui/button";

/**
 * KF-16(docs/improvement.md): 루트 `not-found.tsx`와 `(session)/not-found.tsx`가
 * 같은 본문을 쓴다 — 랜드마크(`<main>`)는 각자의 부모가 이미 갖고 있는지 여부에
 * 따라 다르므로 여기서는 만들지 않는다.
 */
export function NotFoundContent() {
  return (
    <div className="prose stack">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소가 바뀌었거나 삭제된 페이지입니다. 아래에서 원하는 화면으로 이동할 수 있습니다.</p>
      <p>
        <LinkButton href={ROUTES.home}>홈으로 가기</LinkButton>
      </p>
    </div>
  );
}
