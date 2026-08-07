"use client";

import { useEffect, useRef, useState } from "react";

// 뷰포트가 아니라 실제 부모 컨테이너 폭을 추적한다. 사이드바 유무에 따라 콘텐츠
// 컬럼 실폭이 달라지는 광고 슬롯처럼, 미디어쿼리만으로는 판정할 수 없는 지점에 쓴다.
// 마운트 전(SSR/최초 렌더)에는 null을 반환해 "아직 모른다"와 "0px"를 구분한다.
export function useElementWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number | null] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState<number | null>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    setWidth(el.offsetWidth);

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) setWidth(entry.contentRect.width);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return [ref, width];
}
