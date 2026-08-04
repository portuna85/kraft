// 기반 UI 프리미티브의 props/접근성 계약이다. 각 프리미티브 구현은 이 타입을 직접
// 소비해 일관된 크기·상태·키보드·오버레이 동작을 유지한다.
//
// 공통 원칙: "프리미티브는 접근성·크기·상태 계약만 담당하고 도메인 컴포넌트가
// 실제 문구와 데이터를 소유한다" — 그래서 아래 타입에는 문구(label 등)를 제외한
// 구조적 props만 정의한다. 실제 문구는 도메인 컴포넌트(RecommendationModeCard 등)가 넘긴다.

import type { ComponentPropsWithoutRef, ReactNode } from "react";

/** 44x44px 최소 터치 영역을 강제하는 공통 크기 스케일. */
export type PrimitiveSize = "sm" | "md" | "lg";

// ── Button 계열 ────────────────────────────────────────────────────────────

export type ButtonVariant = "primary" | "secondary" | "quiet" | "danger";

// M-5: 나머지 네이티브 button 속성(aria-*, data-*, id, form, className 등)은 그대로
// 통과시킨다 — 구조적 계약(variant/size/loading 등)만 이 타입이 좁힌다.
type ButtonBaseProps = Omit<
  ComponentPropsWithoutRef<"button">,
  "children" | "type" | "disabled" | "onClick"
> & {
  variant: ButtonVariant;
  size?: PrimitiveSize;
  disabled?: boolean;
  type?: "button" | "submit" | "reset";
  onClick?: () => void;
  children: ReactNode;
};

// L-9: loading=true는 children을 보조기술로부터 숨기므로(button.tsx의 aria-hidden),
// 그 상태를 대신 알릴 loadingLabel이 그때만큼은 선택이 아니라 필수다 — 판별 유니온으로
// 타입 차원에서 강제한다(이전에는 optional이라 호출부 누락을 컴파일이 못 잡았다).
export type ButtonContract =
  | (ButtonBaseProps & { loading?: false; loadingLabel?: string })
  | (ButtonBaseProps & { loading: true; loadingLabel: string });

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
  /** 입력값의 형식·글자 수처럼 평상시에 함께 읽어야 하는 안내 요소의 id. */
  descriptionId?: string;
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
  maxLength?: number;
  name?: string;
  autoComplete?: string;
  inputMode?: "text" | "numeric" | "decimal" | "tel" | "email" | "url" | "search";
  disabled?: boolean;
  required?: boolean;
}

export interface TextAreaContract extends FieldValidationContract {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  maxLength?: number;
  rows?: number;
  disabled?: boolean;
  required?: boolean;
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

// ── 표시 계열 ──────────────────────────────────────────────────────────────

export type BadgeTone = "neutral" | "brand" | "success" | "warning" | "danger";

export interface BadgeContract {
  tone: BadgeTone;
  children: ReactNode;
}

export type StatusBadgeStatus = "fresh" | "stale" | "error";

export interface StatusBadgeContract {
  status: StatusBadgeStatus;
  /** 색상만으로 상태를 전달하지 않는다 — 텍스트 라벨 필수. */
  label: string;
}

// ── 레이아웃 계열 ──────────────────────────────────────────────────────────

export interface SurfaceContract {
  /** 디자인 토큰의 표면 단계. */
  level: 1 | 2 | 3;
  children: ReactNode;
}

export interface DividerContract {
  orientation?: "horizontal" | "vertical";
}

// ── 오버레이 계열 ──────────────────────────────────────────────────────────

export interface OverlayFocusContract {
  /** 모달·드로어는 포커스 트랩, Escape 닫기, 닫은 뒤 포커스 복원을 제공해야 한다. */
  open: boolean;
  onClose: () => void;
  /** 닫힌 뒤 포커스를 되돌릴 트리거 요소 참조. */
  restoreFocusRef?: { current: HTMLElement | null };
  /**
   * L-9: 열릴 때 포커스를 받을 요소. 없으면 컨테이너 안의 첫 포커스 가능 요소로
   * 폴백한다(예: Dialog 헤더의 닫기 버튼) — 파괴적 확인처럼 첫 요소가 의도한 컨트롤이
   * 아닐 때 지정한다.
   */
  initialFocusRef?: { current: HTMLElement | null };
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
  /** Visible escape hatch for pointer, keyboard, and assistive-technology users. */
  closeLabel?: string;
  children: ReactNode;
}

export type ToastTone = "neutral" | "success" | "danger";

export interface InlineAlertContract {
  tone: ToastTone;
  title: string;
  description?: string;
}

// ── 상태 계열 ──────────────────────────────────────────────────────────────

/**
 * FE-003: 되돌릴 수 없는 작업의 확인 절차. 이전에는 `window.confirm`(3곳)·5초 유예
 * undo(1곳)·확인 없음(2곳)이 섞여 있어 사용자가 화면마다 다른 규칙을 학습해야 했다.
 * 확인은 전부 이 다이얼로그로 통일한다.
 */
export interface ConfirmDialogContract {
  open: boolean;
  /** 다이얼로그 제목 — 무엇을 하려는지 질문 형태로 쓴다. */
  title: string;
  /** 되돌릴 수 있는지, 무엇이 사라지는지 등 판단에 필요한 사실. */
  description?: string;
  confirmLabel: string;
  cancelLabel?: string;
  /** 진행 중에는 양쪽 버튼을 잠가 중복 실행을 막는다. */
  pending?: boolean;
  /** 실패를 다이얼로그 안에서 알린다 — 닫아 버리면 사용자가 결과를 못 본다. */
  errorMessage?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export interface EmptyStateContract {
  title: string;
  description?: string;
  action?: { label: string; onClick: () => void };
}

export interface ErrorStateContract {
  title: string;
  description?: string;
  /** 재시도 등 복구 행동을 제시한다. */
  retry?: { label: string; onClick: () => void };
  /**
   * FE-008: 화면 전체를 대신하는 블록(기본)과, 다른 콘텐츠 옆에 끼어드는 인라인 표현.
   * 예전에는 인라인 자리마다 `<p>` + 버튼을 손으로 조립해 문구·마크업·복구 수단이
   * 제각각이었다. 표현만 다르고 계약(제목·설명·재시도)은 같다.
   */
  variant?: "block" | "inline";
}
