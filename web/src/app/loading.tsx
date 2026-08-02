import { DataPageLoading } from "@/components/data-page-loading";

// FE-006: 이 폴백은 홈뿐 아니라 전용 loading.tsx가 없는 모든 라우트에 적용된다
// (/community, /community/**, /analysis, /recommend, /recommend/history, /saved,
// /status, /info/*, /ops). variant="home"은 로또 공 스켈레톤을 그리므로 커뮤니티 목록이나
// 약관 페이지를 열 때 콘텐츠와 무관한 공 모양이 먼저 보였다. 중립 변형을 쓴다.
export default function Loading() {
  return <DataPageLoading variant="generic" />;
}
