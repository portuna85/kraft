// EditorialPhoto는 기반 프리미티브가 아닌 콘텐츠 도메인 컴포넌트다. 사진 메타데이터
// 스키마를 props로 받되, 라이선스가 검증된 사진 자산이 아직 없으므로 실제 렌더는
// aspect-ratio만 예약한 자리표시자다. 승인 자산을 등록할 때 next/image로 교체한다.

export interface EditorialPhotoContract {
  /** 사진 메타데이터의 디렉터리명과 동일해야 한다. */
  slug: string;
  /** 정보성 사진은 의미 중심 alt, 장식성 사진은 빈 문자열. */
  alt: string;
  /** meta.json의 aspectRatio(예: "16:9")를 CSS aspect-ratio로 그대로 사용해 CLS를 막는다. */
  aspectRatio: string;
  /** meta.json의 focalPoint — 실제 이미지가 붙으면 object-position에 반영한다. */
  focalPoint?: { x: number; y: number };
}

export function EditorialPhoto({ slug, aspectRatio }: EditorialPhotoContract) {
  // 실제 자산이 없으므로 레이아웃 자리만 예약한다(width/height 대신 aspect-ratio 고정 —
  // alt/focalPoint는 계약에 존재하지만 승인 자산 등록 전에는 렌더에 쓰이지 않는다.
  return <div data-editorial-photo-slug={slug} style={{ aspectRatio }} />;
}
