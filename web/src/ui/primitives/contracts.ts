// Phase 0 산출물(docs/improvement_gpt.md §4.3, §17 Phase 0): 기반 UI 프리미티브의 props/접근성
// 계약 초안이다. 이 파일은 타입만 정의하며 컴포넌트 구현은 없다 — 실제 구현은 Phase 1
// ("토큰, 테마, reset, 기반 프리미티브")에서 이 계약을 기준으로 진행한다. 어디서도
// import되지 않으므로 빌드·런타임에 영향을 주지 않는다.
//
// 공통 원칙(§4.3): "프리미티브는 접근성·크기·상태 계약만 담당하고 도메인 컴포넌트가
// 실제 문구와 데이터를 소유한다" — 그래서 아래 타입에는 문구(label 등)를 제외한
// 구조적 props만 정의한다. 실제 문구는 도메인 컴포넌트(RecommendationModeCard 등)가 넘긴다.

import type { ReactNode } from "react";

/** 44x44px 최소 터치 영역(§7.1)을 강제하는 공통 크기 스케일. */
export type PrimitiveSize = "sm" | "md" | "lg";

// ── Button 계열 ────────────────────────────────────────────────────────────

export type ButtonVariant = "primary" | "secondary" | "quiet" | "danger";

export interface ButtonContract {
  variant: ButtonVariant;
  size?: PrimitiveSize;
  disabled?: boolean;
  loading?: boolean;
  /** loading=true일 때 스크린리더에 상태를 알리기 위한 필수 텍스트. */
  loadingLabel?: string;
  type?: "button" | "submit" | "reset";
  onClick?: () => void;
  children: ReactNode;
}

export interface IconButtonContract {
  /** 시각적 라벨이 없으므로 스크린리더용 이름은 필수. */
  "aria-label": string;
  variant: ButtonVariant;
  size?: PrimitiveSize;
  disabled?: boolean;
  icon: ReactNode;
  onClick?: () => void;
}

// ── 입력 계열 ──────────────────────────────────────────────────────────────

export interface FieldValidationContract {
  /** 필드 오류 여부. true면 aria-invalid, 연결된 오류 메시지 id가 필수. */
  invalid?: boolean;
  /** invalid=true일 때 aria-describedby로 연결될 오류 메시지 요소의 id. */
  errorMessageId?: string;
}

export interface TextFieldContract extends FieldValidationContract {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

export interface NumberFieldContract extends FieldValidationContract {
  id: string;
  label: string;
  value: number | null;
  onChange: (value: number | null) => void;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
}

export interface SearchFieldContract {
  id: string;
  /** §11.4: 검색어는 2~50자 — 프런트 검증은 서버 규칙의 빠른 피드백용 복제. */
  minLength: 2;
  maxLength: 50;
  value: string;
  onChange: (value: string) => void;
  onSubmit: (value: string) => void;
  placeholder?: string;
}

export interface TextAreaContract extends FieldValidationContract {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  maxLength?: number;
  rows?: number;
  disabled?: boolean;
}

// ── 선택 계열 ──────────────────────────────────────────────────────────────

export interface SegmentedControlOption<T extends string = string> {
  value: T;
  label: string;
  disabled?: boolean;
}

export interface SegmentedControlContract<T extends string = string> {
  /** 키보드 화살표 이동, role="radiogroup" 계약. */
  options: readonly SegmentedControlOption<T>[];
  value: T;
  onChange: (value: T) => void;
  "aria-label": string;
}

export interface TabItem<T extends string = string> {
  value: T;
  label: string;
  disabled?: boolean;
}

export interface TabsContract<T extends string = string> {
  items: readonly TabItem<T>[];
  value: T;
  onChange: (value: T) => void;
  /** 각 탭 패널과 tabpanel 요소를 연결하기 위한 id 접두어. */
  panelIdPrefix: string;
}

export interface ChipContract {
  selected?: boolean;
  disabled?: boolean;
  onToggle?: () => void;
  children: ReactNode;
}

// ── 표시 계열 ──────────────────────────────────────────────────────────────

export type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger";

export interface BadgeContract {
  tone: BadgeTone;
  children: ReactNode;
}

export type StatusBadgeStatus = "fresh" | "stale" | "error";

export interface StatusBadgeContract {
  status: StatusBadgeStatus;
  /** §7.1: 색상만으로 상태를 전달하지 않는다 — 텍스트 라벨 필수. */
  label: string;
}

// ── 레이아웃 계열 ──────────────────────────────────────────────────────────

export interface SurfaceContract {
  /** 새 토큰 매핑(docs/phase0-design-tokens-mapping.md §1)의 표면 단계. */
  level: 1 | 2 | 3;
  children: ReactNode;
}

export interface SectionContract {
  /** 접근성 트리에서 영역을 구분하기 위한 heading 레벨. */
  headingLevel: 2 | 3 | 4;
  title: string;
  children: ReactNode;
}

export interface DividerContract {
  orientation?: "horizontal" | "vertical";
}

// ── 오버레이 계열 ──────────────────────────────────────────────────────────

export interface OverlayFocusContract {
  /** §7.1: 모달·드로어는 포커스 트랩, Escape 닫기, 닫은 뒤 포커스 복원을 제공해야 한다. */
  open: boolean;
  onClose: () => void;
  /** 닫힌 뒤 포커스를 되돌릴 트리거 요소 참조. */
  restoreFocusRef?: { current: HTMLElement | null };
}

export interface DialogContract extends OverlayFocusContract {
  titleId: string;
  title: string;
  children: ReactNode;
}

export interface DrawerContract extends OverlayFocusContract {
  side: "left" | "right" | "bottom";
  titleId: string;
  title: string;
  children: ReactNode;
}

export type ToastTone = "neutral" | "success" | "danger";

export interface ToastContract {
  tone: ToastTone;
  message: string;
  /** aria-live 정중도 — 오류는 assertive, 그 외는 polite(§7.1: 과도하게 반복하지 않는다). */
  politeness: "polite" | "assertive";
  durationMs?: number;
  onDismiss: () => void;
}

export interface InlineAlertContract {
  tone: ToastTone;
  title: string;
  description?: string;
}

// ── 상태 계열 ──────────────────────────────────────────────────────────────

export interface SkeletonContract {
  /** 실제 콘텐츠와 동일한 shape을 흉내내기 위한 줄 수. */
  lines?: number;
  variant?: "text" | "card" | "ball";
}

export interface EmptyStateContract {
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

export interface ErrorStateContract {
  title: string;
  description?: string;
  /** 재시도 등 복구 행동 — §16.2 "복구 행동을 제시한다"와 연결. */
  retry?: { label: string; onClick: () => void };
}
