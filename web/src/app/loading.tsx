import { PageSkeleton } from "@/shared/ui/page-skeleton";

// FE-006(레거시): 루트 폴백은 반드시 "generic"이어야 한다. 특정 라우트 모양(예: 홈의
// 로또 공)을 흉내 내면 그 모양이 관련 없는 모든 라우트로 샌다. 라우트별 loading.tsx가
// 없는 곳만 이 폴백을 본다.
export default function Loading() {
  return <PageSkeleton variant="generic" />;
}
