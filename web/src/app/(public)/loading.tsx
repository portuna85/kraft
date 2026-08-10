import { PageSkeleton } from "@/shared/ui/page-skeleton";

// 홈(`/`)의 page.tsx가 이 폴더에 직접 있어(전용 하위 폴더가 없음) 이 loading.tsx는
// 홈 전용 하위 폴더 loading.tsx를 못 만든다. 문제는 이 파일이 자기 하위 loading.tsx가
// 없는 형제 라우트(/data, /info/[slug])에도 그대로 새어 들어간다는 것 — 루트
// loading.tsx와 같은 이유(FE-006)로 "generic"을 쓴다. "home" variant는 홈만을 위한
// 것이라 여기서는 안전하지 않다.
export default function Loading() {
  return <PageSkeleton variant="generic" />;
}
