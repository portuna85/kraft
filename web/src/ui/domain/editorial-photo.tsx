// Phase 1 산출물(docs/improvement_gpt.md §17 Phase 1 "EditorialPhoto 계약만 구현"). §4.3에서는
// "도메인 컴포넌트"로 분류되지만(기반 프리미티브 아님), 로드맵이 Phase 1에 명시적으로 못박아
// 계약만 여기서 만든다. docs/phase0-photo-asset-policy.md의 meta.json 스키마를 props로 받되,
// 승인된 사진 자산이 아직 없으므로(§5.3 "실제 파일은 이 문서 작성 단계에서 추가하지 않는다")
// 실제 렌더는 aspect-ratio만 예약한 빈 자리표시자다 — Phase 2~3에서 실제 자산이 준비되면
// next/image로 교체한다. web/src/ui/domain/은 §8.1 목표 구조(primitives vs domain 분리)를
// 미리 만들어두는 자리이고, 이번엔 이 파일 하나만 들어간다.

export interface EditorialPhotoContract {
  /** docs/phase0-photo-asset-policy.md의 meta.json 디렉터리명과 동일해야 한다. */
  slug: string;
  /** §5.5: 정보성 사진은 의미 중심 alt, 장식성 사진은 빈 문자열. */
  alt: string;
  /** meta.json의 aspectRatio(예: "16:9")를 CSS aspect-ratio로 그대로 사용해 CLS를 막는다. */
  aspectRatio: string;
  /** meta.json의 focalPoint — 실제 이미지가 붙으면 object-position에 반영한다. */
  focalPoint?: { x: number; y: number };
}

export function EditorialPhoto({ slug, aspectRatio }: EditorialPhotoContract) {
  // 실제 자산이 없으므로 레이아웃 자리만 예약한다(width/height 대신 aspect-ratio 고정 —
  // §5.5). alt/focalPoint는 계약에 존재하지만 아직 렌더에 쓰이지 않는다.
  return <div data-editorial-photo-slug={slug} style={{ aspectRatio }} />;
}
