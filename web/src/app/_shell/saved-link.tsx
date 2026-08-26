"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { ROUTES } from "@/shared/config/routes";

import { isRouteCurrent } from "./nav-items";
import styles from "./shell.module.css";

/**
 * kraft-redesign-plan.md P0: "보관함"은 4대 축(번호 뽑기·진단·인사이트·커뮤니티)
 * 어디에도 속하지 않는 개인 유틸리티라 `PRIMARY_NAV`에서 빠지고 헤더의 계정/테마
 * 옆(utility nav)으로 옮겼다. `PrimaryNav`와 같은 이유로 이 링크만 별도 클라이언트
 * 리프로 둔다 — `SiteHeader` 자체는 서버 컴포넌트로 남긴다.
 */
export function SavedLink() {
  const pathname = usePathname();

  return (
    <Link
      className={styles.utilityLink}
      href={ROUTES.saved}
      aria-current={isRouteCurrent(ROUTES.saved, pathname) ? "page" : undefined}
    >
      보관함
    </Link>
  );
}
