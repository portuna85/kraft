import Link from "next/link";

import { ROUTES } from "@/shared/config/routes";

export default function NotFound() {
  return (
    <main id="main" className="shell prose stack">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소가 바뀌었거나 삭제된 페이지입니다. 아래에서 원하는 화면으로 이동할 수 있습니다.</p>
      <p>
        <Link href={ROUTES.home}>홈으로 가기</Link>
      </p>
    </main>
  );
}
