import { INFO_PAGE_SLUGS, type InfoPageSlug } from "@/shared/config/routes";

/**
 * 안내 페이지 레지스트리 불변식
 *
 * 페이지 제목·설명·최종 수정일의 단일 소스다. 사이트맵과 푸터도 여기를 읽는다.
 *
 * `Record<InfoPageSlug, …>`로 선언한 것이 핵심이다. shared/config/routes.ts의
 * INFO_PAGE_SLUGS에 슬러그를 추가하면 여기서 **컴파일 에러**가 난다 — 라우트는 생겼는데
 * 제목이 없는 페이지가 조용히 배포되는 일이 없다(레거시 FE-007 규칙 승계).
 */
export type InfoPageMeta = {
  title: string;
  description: string;
  /** 사이트맵의 lastmod. 내용을 고치면 함께 올린다. */
  lastModified: string;
  changeFrequency: "monthly" | "yearly";
  priority: number;
};

export const INFO_PAGE_META: Record<InfoPageSlug, InfoPageMeta> = {
  faq: {
    title: "자주 묻는 질문",
    description: "추천 번호, 보관함, 데이터 반영 시점 등 자주 묻는 질문을 모았습니다.",
    lastModified: "2026-07-29",
    changeFrequency: "monthly",
    priority: 0.6,
  },
  privacy: {
    title: "개인정보처리방침",
    description:
      "KRAFT Lotto의 기기 토큰, OAuth 커뮤니티 계정, 로그와 광고 처리 기준을 안내합니다.",
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
  contact: {
    title: "문의하기",
    description: "서비스 오류, 개선 제안, 데이터 수정 요청을 보낼 수 있는 연락처를 안내합니다.",
    lastModified: "2026-01-01",
    changeFrequency: "monthly",
    priority: 0.5,
  },
  about: {
    title: "운영자 소개",
    description: "KRAFT Lotto를 운영하는 주체와 서비스 목적, 문의 경로를 안내합니다.",
    lastModified: "2026-07-31",
    changeFrequency: "yearly",
    priority: 0.4,
  },
};

export function isInfoPageSlug(slug: string): slug is InfoPageSlug {
  return (INFO_PAGE_SLUGS as readonly string[]).includes(slug);
}
