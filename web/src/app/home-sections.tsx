import Link from "next/link";
import { DataFreshnessNote } from "@/components/data-freshness-note";
import { PrizeTable } from "@/components/prize-table";
import type { HomeCommunityPostSummary, RoundFreshness, WinningNumber } from "@/lib/api";
import { formatDateTime, formatDrawDate } from "@/lib/format";
import {
  communityPopularPostsAlreadyVisible,
  HOME_LATEST_POST_LIMIT,
  HOME_POPULAR_POST_LIMIT,
} from "@/lib/home-community-sections";
import { LottoBalls } from "@/ui/domain/lotto-balls";
import styles from "./home-sections.module.css";

export function HomeHero({ latest }: { latest: WinningNumber }) {
  const orbitNumbers = [latest.numbers[0], latest.numbers[3], latest.numbers[5]];
  return (
    <section className={styles.hero} aria-labelledby="home-hero-title">
      <div className={styles.heroCopy}>
        <p className={styles.kicker}><span className={styles.liveDot} aria-hidden="true" />LIVE DATA · 동행복권 공식 데이터 기반</p>
        <h1 id="home-hero-title" className={styles.title}>숫자를 보는 <span className={styles.gradientText}>새로운 방식.</span></h1>
        <p className={styles.lead}>최신 당첨 결과, 번호 추천, 패턴 분석과 커뮤니티를 하나의 명확한 경험으로 연결합니다.</p>
        <div className={styles.actions}>
          <Link href="/recommend" className={styles.primaryAction}>오늘의 번호 만들기</Link>
          <Link href="#latest-result" className={styles.secondaryAction}>최신 결과 보기</Link>
        </div>
      </div>
      <div className={styles.orbStage} aria-hidden="true">
        <div className={styles.orb}>
          <span className={styles.ring} />
          <span className={styles.ringOuter} />
          {orbitNumbers.map((number) => <span key={number} className={styles.orbBall}>{number}</span>)}
        </div>
      </div>
    </section>
  );
}

export function HomeMetrics({ latest, freshness }: { latest: WinningNumber; freshness: RoundFreshness | null }) {
  const metrics = [
    { value: "1 / 8,145,060", label: "모든 조합의 동일한 1등 확률" },
    { value: `${latest.round}회`, label: "최신 반영 회차" },
    { value: "1회부터", label: "공식 당첨 이력 제공" },
    { value: freshness?.fresh ? "최신 반영" : freshness ? "반영 지연" : "확인 중", label: "데이터 최신성" },
  ];
  return (
    <section className={styles.metrics} aria-label="서비스 핵심 정보">
      {metrics.map((metric) => (
        <div key={metric.label} className={styles.metric}>
          <strong className={styles.metricValue}>{metric.value}</strong>
          <span className={styles.metricLabel}>{metric.label}</span>
        </div>
      ))}
    </section>
  );
}

export function LatestResultSection({ latest, freshness }: { latest: WinningNumber; freshness: RoundFreshness | null }) {
  return (
    <section id="latest-result" className={styles.section} aria-labelledby="latest-result-title">
      <div className={styles.sectionHeader}>
        <p className={styles.eyebrow}>Latest result</p>
        <h2 id="latest-result-title" className={styles.sectionTitle}>이번 주 당첨 결과</h2>
        <p className={styles.sectionDescription}>당첨 번호와 1·2등 세전 금액, 예상 세후 금액을 한 화면에서 확인하세요.</p>
      </div>
      <div className={`${styles.resultCard} result-panel hero-panel`}>
        <div className={styles.resultMain}>
          <h3 className={styles.round}>{latest.round}회 당첨 결과</h3>
          <p className={styles.drawDate}>{formatDrawDate(latest.drawDate)}</p>
          <LottoBalls numbers={latest.numbers} bonusNumber={latest.bonusNumber} />
          <DataFreshnessNote freshness={freshness} />
        </div>
        <div className={styles.prizePanel}>
          <PrizeTable firstPrizeAmount={latest.firstPrizeAmount} secondPrize={latest.secondPrize} />
        </div>
      </div>
    </section>
  );
}

const FEATURES = [
  { href: "/frequency", icon: "↗", title: "출현 통계", description: "번호별 빈도와 전체 회차의 상대적인 분포를 확인합니다." },
  { href: "/stats", icon: "⌁", title: "패턴 분석", description: "홀짝, 고저, 합계 구간의 과거 분포를 비교합니다." },
  { href: "/companion", icon: "◎", title: "동반 출현", description: "선택한 번호와 함께 나온 번호의 과거 이력을 탐색합니다." },
  { href: "/analysis", icon: "◇", title: "번호 분석", description: "선택한 여섯 번호의 이력과 통계적 특징을 점검합니다." },
  { href: "/saved", icon: "▣", title: "보관함", description: "추천 조합과 관심 번호를 저장하고 당첨 결과를 확인합니다." },
  { href: "/info/responsible-play", icon: "◌", title: "책임 있는 이용", description: "모든 조합의 확률이 동일하다는 원칙과 이용 기준을 안내합니다." },
] as const;

export function DataFeatures() {
  return (
    <section className={styles.section} aria-labelledby="data-features-title">
      <div className={styles.sectionHeader}>
        <p className={styles.eyebrow}>Data intelligence</p>
        <h2 id="data-features-title" className={styles.sectionTitle}>데이터를 이해하기 쉽게</h2>
        <p className={styles.sectionDescription}>복잡한 과거 통계를 카드와 명확한 지표로 단순화해 탐색을 돕습니다.</p>
      </div>
      <div className={styles.featureGrid}>
        {FEATURES.map((feature) => (
          <Link key={feature.href} href={feature.href} className={styles.feature}>
            <span className={styles.featureIcon} aria-hidden="true">{feature.icon}</span>
            <h3>{feature.title}</h3>
            <p>{feature.description}</p>
            <span className={styles.featureCta}>살펴보기 →</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

function PostList({ posts, unavailable }: { posts: HomeCommunityPostSummary[]; unavailable: boolean }) {
  if (unavailable) return <p role="status">게시글을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</p>;
  if (posts.length === 0) return <p className="muted">아직 공개된 글이 없습니다.</p>;
  return (
    <ul className={styles.postList}>
      {posts.slice(0, HOME_LATEST_POST_LIMIT).map((post, index) => (
        <li key={post.id} className={styles.post}>
          <Link href={`/community/posts/${post.id}`}>
            <span className={styles.postTitle}>{post.title}</span>
            <span className={styles.postMeta}>{post.authorNameSnapshot} · <time dateTime={post.createdAt}>{formatDateTime(post.createdAt)}</time></span>
          </Link>
          {index === 0 ? <span className={styles.postMarker}>● 최신</span> : null}
        </li>
      ))}
    </ul>
  );
}

export function HomeCommunity({ latestPosts, popularPosts, unavailable }: { latestPosts: HomeCommunityPostSummary[]; popularPosts: HomeCommunityPostSummary[]; unavailable: boolean }) {
  const popularDuplicatesLatest = communityPopularPostsAlreadyVisible(latestPosts, popularPosts);

  return (
    <section className={styles.section} aria-labelledby="home-community-title">
      <div className={styles.sectionHeader}>
        <p className={styles.eyebrow}>Community</p>
        <h2 id="home-community-title" className={styles.sectionTitle}>함께 나누는 이번 주 이야기</h2>
        <p className={styles.sectionDescription}>최신 글과 최근 7일 동안 주목받은 이야기를 실제 커뮤니티 데이터로 보여드립니다.</p>
      </div>
      <div className={styles.communityGrid}>
        <PostList posts={latestPosts} unavailable={unavailable} />
        <aside className={styles.popularCard} aria-labelledby="popular-title">
          <span className={styles.featureIcon} aria-hidden="true">★</span>
          <h3 id="popular-title">이번 주 인기</h3>
          <p>최근 7일 공개 글을 반응과 최신성을 기준으로 정렬했습니다.</p>
          {!unavailable && popularDuplicatesLatest ? (
            <p className="muted">최근 글과 이번 주 인기 글이 같아 한 번만 표시합니다.</p>
          ) : !unavailable && popularPosts.length > 0 ? (
            <ul className={styles.popularList}>
              {popularPosts.slice(0, HOME_POPULAR_POST_LIMIT).map((post) => <li key={post.id}><Link href={`/community/posts/${post.id}`}>{post.title}</Link></li>)}
            </ul>
          ) : <p className="muted">{unavailable ? "인기 글을 불러오지 못했습니다." : "아직 집계된 인기 글이 없습니다."}</p>}
          <Link href="/community?sort=weekly_popular" className={styles.primaryAction}>커뮤니티 둘러보기</Link>
        </aside>
      </div>
    </section>
  );
}

export function HomeCta() {
  return (
    <section className={styles.cta} aria-labelledby="home-cta-title">
      <div className={styles.sectionHeader}>
        <p className={styles.eyebrow}>Ready to pick?</p>
        <h2 id="home-cta-title" className={styles.sectionTitle}>오늘의 조합을 만들어보세요.</h2>
        <p className={styles.sectionDescription}>모든 유효한 조합의 당첨 확률은 같습니다. 추천과 과거 통계는 미래 결과를 예측하거나 당첨을 보장하지 않습니다.</p>
      </div>
      <div className={styles.actions}>
        <Link href="/recommend" className={styles.primaryAction}>번호 추천 시작</Link>
        <Link href="/info/methodology" className={styles.secondaryAction}>분석 기준 보기</Link>
      </div>
    </section>
  );
}
