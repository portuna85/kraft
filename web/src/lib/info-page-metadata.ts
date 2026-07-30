export type InfoPageMetadata = {
  title: string;
  description: string;
  lastModified: string;
  changeFrequency: "monthly" | "yearly";
  priority: number;
};

/**
 * Single source of truth for public information pages.
 * Page metadata, static generation, breadcrumbs, and the sitemap all consume
 * this registry so legal/help content cannot silently drift between surfaces.
 */
export const INFO_PAGE_METADATA = {
  "data-source": {
    title: "데이터 출처",
    description: "KRAFT Lotto에서 사용하는 당첨 결과 데이터의 출처와 반영 기준을 안내합니다.",
    lastModified: "2026-01-01",
    changeFrequency: "monthly",
    priority: 0.5,
  },
  methodology: {
    title: "분석 방법론",
    description: "빈도, 패턴, 동반 출현 등 KRAFT Lotto 통계 화면의 계산 기준을 설명합니다.",
    lastModified: "2026-07-29",
    changeFrequency: "monthly",
    priority: 0.5,
  },
  faq: {
    title: "자주 묻는 질문",
    description: "추천 번호, 저장함, 데이터 반영 시점 등 자주 묻는 질문을 모았습니다.",
    lastModified: "2026-07-29",
    changeFrequency: "monthly",
    priority: 0.6,
  },
  privacy: {
    title: "개인정보처리방침",
    description: "KRAFT Lotto의 기기 토큰, OAuth 커뮤니티 계정, 로그와 광고 처리 기준을 안내합니다.",
    lastModified: "2026-06-24",
    changeFrequency: "yearly",
    priority: 0.4,
  },
  terms: {
    title: "이용약관",
    description: "KRAFT Lotto 서비스 이용 조건과 책임 범위를 안내합니다.",
    lastModified: "2026-01-01",
    changeFrequency: "yearly",
    priority: 0.4,
  },
  contact: {
    title: "문의하기",
    description: "서비스 오류, 개선 제안, 데이터 수정 요청을 보낼 수 있는 연락처를 안내합니다.",
    lastModified: "2026-01-01",
    changeFrequency: "monthly",
    priority: 0.5,
  },
  "responsible-play": {
    title: "건전한 이용",
    description: "로또를 무리 없이 즐기기 위한 기본 원칙과 도움 받을 수 있는 기관을 안내합니다.",
    lastModified: "2026-01-01",
    changeFrequency: "monthly",
    priority: 0.5,
  },
  "community-guidelines": {
    title: "커뮤니티 이용규칙",
    description: "KRAFT Lotto 커뮤니티에서 지켜야 할 이용 규칙과 신고 처리 방식을 안내합니다.",
    lastModified: "2026-07-30",
    changeFrequency: "monthly",
    priority: 0.4,
  },
} as const satisfies Record<string, InfoPageMetadata>;

export type InfoPageSlug = keyof typeof INFO_PAGE_METADATA;

export const INFO_PAGE_SLUGS = Object.keys(INFO_PAGE_METADATA) as InfoPageSlug[];

export function isInfoPageSlug(slug: string): slug is InfoPageSlug {
  return Object.hasOwn(INFO_PAGE_METADATA, slug);
}
