import type { Metadata } from "next";
import Link from "next/link";

import { ROUTES } from "@/shared/config/routes";
import { Card } from "@/shared/ui/surface";

import styles from "./data.module.css";

export const metadata: Metadata = {
  title: "로또 통계와 번호 분석",
  description:
    "번호별 출현 빈도, 홀짝·고저·합계 패턴, 함께 나온 번호, 내 조합 진단까지 데이터 기능 4가지를 한 곳에서 찾을 수 있습니다.",
  alternates: { canonical: "/data" },
};

/**
 * `/data` 허브
 *
 * 모바일에서 데이터 기능의 1차 진입점이다. 모바일 하단 탭이 5개 고정이라
 * (`app/_shell/nav-items.ts` `TAB_BAR_ITEMS`), "통계"(`/frequency` 단독 링크) 대신
 * 이 허브가 그 자리를 대체했다 — `/frequency`는 URL로 계속 살아있고 이 허브의
 * 카드와 데스크톱 내비에서 계속 접근 가능하다.
 *
 * 대형 차트를 이 허브 자체에 두지 않는다(codex 명시) — 카드 4개는 각 기능으로
 * 가는 안내일 뿐이다.
 */
const FEATURES: Array<{ href: string; title: string; description: string }> = [
  { href: ROUTES.frequency, title: "출현 통계", description: "번호별 누적·기간별 출현 횟수" },
  { href: ROUTES.stats, title: "패턴 통계", description: "홀짝·고저·합계 구간 분포" },
  { href: ROUTES.companion, title: "동반 출현", description: "두 번호가 함께 등장한 빈도" },
  { href: ROUTES.analysis, title: "번호 분석", description: "내가 선택한 6개 번호의 패턴 검사" },
];

export default function DataHubPage() {
  return (
    <div className="stack">
      <header className="prose stack">
        <h1>데이터와 분석</h1>
        <p>
          과거 회차 통계는 다음 회차 당첨을 예측하지 않습니다. 여기 모은 기능은 과거 기록을 요약해
          보여줄 뿐입니다.
        </p>
      </header>

      <ul className={styles.grid}>
        {FEATURES.map((feature) => (
          <Card as="li" level={2} key={feature.href}>
            <h2>{feature.title}</h2>
            <p className="note">{feature.description}</p>
            {/* I-33: "보기" 텍스트만 클릭 대상이라 카드처럼 보이는 나머지 영역을
                눌러도 반응이 없었다 — stretched-link로 카드 전체를 링크 히트
                영역에 포함시킨다.
                RSP-28(docs/improvement.md): 네 카드 모두 접근 이름이 "보기"로
                같아 스크린리더 링크 목록에서 목적지를 구별할 수 없었다. 시각
                라벨은 "보기"로 유지하고 sr-only 텍스트를 앞에 조합해 접근
                이름만 "출현 통계 보기"처럼 고유하게 만든다. */}
            <Link href={feature.href} className={styles.cardLink}>
              <span className="sr-only">{feature.title} </span>
              보기
            </Link>
          </Card>
        ))}
      </ul>
    </div>
  );
}
