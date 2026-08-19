import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { __resetScrollLockForTests, useScrollLock } from "./use-scroll-lock";

describe("useScrollLock", () => {
  afterEach(() => {
    __resetScrollLockForTests();
  });

  it("활성화되면 body 스크롤을 잠그고, 비활성화되면 원래 값으로 복원한다", () => {
    document.body.style.overflow = "auto";

    const { rerender, unmount } = renderHook(({ active }) => useScrollLock(active), {
      initialProps: { active: true },
    });
    expect(document.body.style.overflow).toBe("hidden");

    rerender({ active: false });
    expect(document.body.style.overflow).toBe("auto");

    unmount();
  });

  it("KF-17(docs/improvement.md): 오버레이 2개가 겹치면 하나가 닫혀도 잠금이 유지된다", () => {
    document.body.style.overflow = "";

    const a = renderHook(({ active }) => useScrollLock(active), {
      initialProps: { active: true },
    });
    const b = renderHook(({ active }) => useScrollLock(active), {
      initialProps: { active: true },
    });
    expect(document.body.style.overflow).toBe("hidden");

    a.unmount();
    expect(document.body.style.overflow).toBe("hidden");

    b.unmount();
    expect(document.body.style.overflow).toBe("");
  });

  it("__resetScrollLockForTests가 카운터와 overflow를 초기화한다", () => {
    const { unmount } = renderHook(() => useScrollLock(true));
    expect(document.body.style.overflow).toBe("hidden");

    __resetScrollLockForTests();
    expect(document.body.style.overflow).toBe("");

    unmount();
  });
});
