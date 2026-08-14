import { ROUTES } from "@/shared/config/routes";

/**
 * 내비게이션 구성
 *
 * **URL은 전부 유지하고 라벨과 묶음만 바꾼다**(R-13). 전환 후 이탈률을 관찰하려면
 * 바뀐 것이 IA뿐이어야 한다 — URL까지 같이 바뀌면 원인을 분리할 수 없다.
 */

export type NavItem = { href: string; label: string };
export type NavGroup = { title: string; items: NavItem[] };

export const PRIMARY_NAV: NavGroup[] = [
  {
    title: "번호 뽑기",
    items: [
      { href: ROUTES.recommend, label: "번호 추천" },
      { href: ROUTES.analysis, label: "내 조합 진단" },
      { href: ROUTES.saved, label: "보관함" },
    ],
  },
  {
    title: "통계 보기",
    items: [
      { href: ROUTES.data, label: "데이터" },
      { href: ROUTES.frequency, label: "번호별 출현" },
      { href: ROUTES.stats, label: "당첨 패턴" },
      { href: ROUTES.companion, label: "함께 나온 번호" },
    ],
  },
  {
    title: "커뮤니티",
    items: [{ href: ROUTES.community, label: "커뮤니티" }],
  },
];

/** 모바일 하단 탭. 5개를 넘기면 터치 타깃이 44px 아래로 내려간다(§12.7). */
export const TAB_BAR_ITEMS: NavItem[] = [
  { href: ROUTES.home, label: "홈" },
  { href: ROUTES.recommend, label: "추천" },
  { href: ROUTES.data, label: "데이터" },
  { href: ROUTES.community, label: "커뮤니티" },
  { href: ROUTES.saved, label: "보관함" },
];

export const FOOTER_NAV: NavGroup[] = [
  {
    title: "서비스 안내",
    items: [
      { href: ROUTES.info("about"), label: "운영자 소개" },
      { href: ROUTES.info("data-source"), label: "데이터 출처" },
      { href: ROUTES.info("methodology"), label: "분석 방법론" },
      { href: ROUTES.status, label: "서비스 상태" },
    ],
  },
  {
    title: "이용 안내",
    items: [
      { href: ROUTES.info("faq"), label: "자주 묻는 질문" },
      { href: ROUTES.info("community-guidelines"), label: "커뮤니티 이용규칙" },
      { href: ROUTES.info("responsible-play"), label: "건전한 이용" },
      { href: ROUTES.info("contact"), label: "문의하기" },
    ],
  },
  {
    title: "정책",
    items: [
      { href: ROUTES.info("terms"), label: "이용약관" },
      { href: ROUTES.info("privacy"), label: "개인정보처리방침" },
    ],
  },
];
