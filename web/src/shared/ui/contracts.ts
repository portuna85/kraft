import type { ReactNode } from "react";

/**
 * 프리미티브 접근성 계약 — improvement_fe.md §8.6, §9.3, §12.1
 *
 * 이 파일의 목적은 타입 문서가 아니라 **강제**다. 접근성 규칙을 리뷰 체크리스트로 두면
 * 언젠가 빠지지만, 판별 유니온으로 두면 빠뜨린 순간 컴파일이 깨진다. 레거시의 계약이
 * Radix보다 이 프로젝트에 잘 맞았던 이유가 이것이고, 그래서 **의미는 승계하되 구현은
 * 새로 쓴다**.
 *
 * 각 계약 옆 주석은 "왜 이 규칙이 있는지"다 — 규칙만 남고 이유가 사라지면 다음 사람이
 * 편의를 위해 규칙을 지운다.
 */

export type Size = "sm" | "md" | "lg";

/* ── Button ──────────────────────────────────────────────────────────── */

export type ButtonVariant = "primary" | "secondary" | "quiet" | "danger" | "dangerQuiet";

type ButtonBase = {
  variant?: ButtonVariant;
  size?: Size;
  disabled?: boolean;
  children: ReactNode;
};

/**
 * loading일 때 loadingLabel이 **타입 차원에서 필수**다.
 * 스피너만 도는 버튼은 스크린리더 사용자에게 아무 일도 일어나지 않은 것과 같다.
 */
export type ButtonContract = ButtonBase &
  ({ loading?: false; loadingLabel?: never } | { loading: true; loadingLabel: string });

/** 아이콘만 있는 버튼은 접근 이름이 없다 — aria-label을 필수로 둔다. */
export type IconButtonContract = Omit<ButtonBase, "children"> & {
  "aria-label": string;
  icon: ReactNode;
} & ({ loading?: false; loadingLabel?: never } | { loading: true; loadingLabel: string });

/**
 * 버튼처럼 보이는 링크 — 빈 상태 CTA 등 "누르면 이동"인 자리에 쓴다
 * (improvement_fe_codex.md §12.10/§12.11). `disabled`·`loading`이 없다 — 링크는
 * 눌러도 네비게이션만 할 뿐 진행 상태를 가질 수 없다(Button과 의미가 다르다).
 */
export type LinkButtonContract = {
  variant?: ButtonVariant;
  size?: Size;
  href: string;
  children: ReactNode;
};

/* ── 상태 표시 ───────────────────────────────────────────────────────── */

export type Tone = "neutral" | "brand" | "success" | "warning" | "danger";

/**
 * label이 필수라 색상만으로 상태를 전달하는 것이 불가능하다.
 * 색각 이상 사용자와 흑백 출력에서 배지가 무의미해지는 것을 막는다.
 */
export type BadgeContract = { tone?: Tone; size?: "sm" | "md"; label: string };

export type FreshnessStatus = "fresh" | "stale" | "error";

/** 데이터 신선도 배지도 같은 이유로 텍스트 라벨이 필수다(§3.1). */
export type StatusBadgeContract = { status: FreshnessStatus; label: string };

/**
 * PageHeader — improvement_fe.md §23.14. `title`은 그 페이지의 `<h1>`이다 —
 * 호출부가 별도로 `<h1>`을 또 두면 페이지에 제목이 두 개가 된다.
 */
export type PageHeaderContract = {
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
};

/** Stat — 요약 그리드의 키/값 타일 하나. `tone`은 값 자체의 의미(위험·정상 등)에만 쓴다. */
export type StatContract = { label: string; value: ReactNode; tone?: Tone };

/* ── 오버레이 ────────────────────────────────────────────────────────── */

export type DialogContract = {
  open: boolean;
  onClose: () => void;
  /** 제목은 aria-labelledby로 연결된다 — 이름 없는 다이얼로그를 만들 수 없다. */
  title: string;
  size?: Size;
  children: ReactNode;
};

/**
 * 확인 다이얼로그는 pending·errorMessage를 계약에 포함한다.
 * "확인 중 실패"를 다이얼로그 **안에서** 처리하도록 강제하기 위함이다 — 밖으로 내보내면
 * 다이얼로그가 닫힌 뒤 어디에도 안 보이는 오류가 생긴다.
 */
export type ConfirmDialogContract = Omit<DialogContract, "children"> & {
  variant?: "default" | "danger";
  description: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  pending?: boolean;
  errorMessage?: string | null;
};

/* ── 상태 화면 ───────────────────────────────────────────────────────── */

/**
 * 빈 상태는 **원인을 구분**해야 한다. "검색 결과 없음"과 "게시판이 비어 있음"을 같은
 * 문구로 보여주면 사용자는 필터를 지워야 한다는 것을 알 수 없다(레거시 FE-048).
 */
export type EmptyStateContract = {
  reason: "no-data" | "filtered";
  title: string;
  description: ReactNode;
  action?: ReactNode;
};

/** 오류 상태에는 **복구 액션이 필수**다. 막다른 길을 만들지 않는다. */
export type ErrorStateContract = {
  layout?: "block" | "inline";
  title: string;
  description: ReactNode;
  action: ReactNode;
};

/* ── 탐색 ────────────────────────────────────────────────────────────── */

/**
 * 브레드크럼의 마지막 항목은 **현재 위치**라 링크가 아니다.
 *
 * 그래서 items에는 조상만 담고 현재 페이지 이름은 `current`로 따로 받는다. 전부를
 * 한 배열에 담고 "마지막은 링크로 만들지 말자"를 호출부가 기억하게 두면 언젠가
 * 자기 자신으로 가는 링크가 생긴다.
 */
export type BreadcrumbContract = {
  items: readonly { label: string; href: string }[];
  current: string;
};

/**
 * 아코디언 항목은 열림 상태를 스스로 관리한다.
 *
 * `<button aria-expanded>`와 패널이 짝을 이뤄야 하므로 id 연결을 컴포넌트가 책임진다 —
 * 호출부에 맡기면 id가 겹치거나 어긋나 스크린리더에서 패널이 열린 것을 알 수 없다.
 */
export type AccordionContract = {
  /** 목록 전체의 이름. 아코디언이 여러 개인 페이지에서 서로 구분된다. */
  label: string;
  items: readonly { id: string; question: string; answer: ReactNode }[];
};
