"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";

import styles from "./overlay.module.css";

/**
 * Toast — improvement_fe.md §9.3
 *
 * 자동으로 닫히는 알림의 최소 노출 시간은 6초다. 그보다 짧으면 화면을 순차적으로 읽는
 * 사용자가 메시지를 놓친다. 영역은 `aria-live="polite"`라 진행 중인 낭독을 끊지 않는다.
 *
 * **되돌릴 수 없는 작업의 결과를 토스트로만 알리지 않는다** — 사라지는 UI에 중요한
 * 정보를 두면 놓친 사용자에게는 아무 일도 일어나지 않은 것과 같다.
 */
const MIN_VISIBLE_MS = 6_000;

type Toast = { id: number; message: string };
type ToastContextValue = { show: (message: string) => void };

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const show = useCallback((message: string) => {
    setToasts((current) => [...current, { id: Date.now() + current.length, message }]);
  }, []);

  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className={styles.toastRegion} aria-live="polite">
        {toasts.map((toast) => (
          <ToastItem
            key={toast.id}
            message={toast.message}
            onExpire={() => setToasts((current) => current.filter((item) => item.id !== toast.id))}
          />
        ))}
      </div>
    </ToastContext.Provider>
  );
}

function ToastItem({ message, onExpire }: { message: string; onExpire: () => void }) {
  useEffect(() => {
    const timer = window.setTimeout(onExpire, MIN_VISIBLE_MS);
    return () => window.clearTimeout(timer);
    // onExpire는 매 렌더 새로 만들어지므로 의존성에 넣으면 타이머가 계속 리셋된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <div className={styles.toast}>{message}</div>;
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (context === null) {
    throw new Error("useToast는 ToastProvider 안에서만 쓸 수 있습니다.");
  }
  return context;
}
