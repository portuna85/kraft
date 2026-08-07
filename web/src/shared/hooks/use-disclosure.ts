"use client";

import { useCallback, useState } from "react";

/**
 * 열림/닫힘 상태 — improvement_fe.md §22.4
 *
 * 별것 아닌 훅이지만 있는 편이 낫다. 각 컴포넌트가 useState로 재발명하면 토글 함수가
 * 매 렌더 새로 생기고, 그 함수를 이펙트 의존성에 넣은 곳에서 무한 재구독이 난다
 * (레거시 FE-099가 정확히 그 사고다).
 */
export function useDisclosure(initialOpen = false) {
  const [isOpen, setIsOpen] = useState(initialOpen);

  const open = useCallback(() => setIsOpen(true), []);
  const close = useCallback(() => setIsOpen(false), []);
  const toggle = useCallback(() => setIsOpen((current) => !current), []);

  return { isOpen, open, close, toggle };
}
