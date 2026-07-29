import {
  REVALIDATE_LATEST,
  REVALIDATE_STATS,
  TAG_ROUNDS_LATEST,
  TAG_STATS,
} from "@/lib/revalidate";
import type { components } from "@/lib/generated/api-types";
import { backendBaseUrl } from "@/lib/backend-url";

// F-03: 백엔드 OpenAPI 계약(B-01, /v3/api-docs)에서 생성된 스키마를 그대로 쓰지 않고
// 이 유틸로 감싼다 — springdoc은 record 필드의 not-null 보장을 모르므로 모든 필드를
// optional로 생성한다. 백엔드는 항상 완전한 레코드를 반환하므로, 손으로 유지하던
// 타입들과 동일하게 전 필드를 필수로 되돌린다(중첩 객체·배열까지 재귀 적용).
export type RequiredApi<T> = {
  [K in keyof T]-?: NonNullable<T[K]> extends (infer U)[]
    ? RequiredApi<U>[]
    : NonNullable<T[K]> extends object
      ? RequiredApi<NonNullable<T[K]>>
      : NonNullable<T[K]>;
};

const publicBaseUrl = resolvePublicBaseUrl();

function resolvePublicBaseUrl(): string {
  const value = process.env.KRAFT_PUBLIC_BASE_URL;
  if (value) return value;
  // 프로덕션에서 미설정이면 metadataBase·OG·JSON-LD·sitemap·robots 전부가 조용히
  // http://localhost로 새어나가므로, 조용한 폴백 대신 기동 시점에 크게 실패시킨다 —
  // 단, 이 모듈이 여러 클라이언트 컴포넌트에서 import돼 번들러가 공용 청크로 묶으면
  // 브라우저에서도 이 파일이 평가된다. KRAFT_PUBLIC_BASE_URL은 NEXT_PUBLIC_ 접두사가
  // 없어 클라이언트 번들에는 항상 undefined이므로, 서버 쪽 배포 오류를 잡으려던
  // 이 체크가 브라우저에서 매번 던지는 사고가 난다(§ad-overlay e2e 회귀). 서버에서
  // 평가될 때만 이 검증을 적용한다.
  if (typeof window === "undefined" && process.env.NODE_ENV === "production") {
    throw new Error("KRAFT_PUBLIC_BASE_URL 환경변수가 설정되지 않았습니다.");
  }
  return "http://localhost";
}

export type WinningNumber = RequiredApi<components["schemas"]["WinningNumberResponse"]>;

export type RoundFreshness = RequiredApi<components["schemas"]["RoundFreshnessResponse"]>;

export type HomeCommunityPostSummary = RequiredApi<components["schemas"]["HomeCommunityPostSummary"]>;

export type HomeSummary = Omit<RequiredApi<components["schemas"]["HomeResponse"]>, "latestRound" | "freshness"> & {
  latestRound: WinningNumber | null;
  freshness: RoundFreshness | null;
};

export type PublicIncident = {
  round: number | null;
  type: string;
  resolved: boolean;
  occurredAt: string;
  occurrences: number;
};

type RequestInitWithNext = RequestInit & {
  next?: {
    revalidate?: number;
    tags?: string[];
  };
};

// W-3: 백엔드 에러 코드/메시지를 보존하는 커스텀 에러
export class BackendError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number
  ) {
    super(message);
    this.name = "BackendError";
  }
}

async function fetchJson<T>(path: string, init?: RequestInitWithNext): Promise<T> {
  // W-1: SSR 렌더 무한 대기 방지
  const signal = AbortSignal.timeout(5000);
  const response = await fetch(`${backendBaseUrl}${path}`, { signal, ...init });
  if (!response.ok) {
    let code = "BACKEND_ERROR";
    let message = `Backend request failed: ${path} (${response.status})`;
    try {
      const body = await response.clone().json() as { code?: string; message?: string };
      if (body.code) code = body.code;
      if (body.message) message = body.message;
    } catch {
      // 바디 파싱 실패 시 기본 메시지 유지
    }
    throw new BackendError(code, message, response.status);
  }
  return response.json() as Promise<T>;
}

export function getPublicBaseUrl(): string {
  return publicBaseUrl;
}

export async function getLatestWinningNumber(): Promise<WinningNumber> {
  return fetchJson<WinningNumber>("/api/v1/rounds/latest", {
    next: { revalidate: REVALIDATE_LATEST, tags: [TAG_ROUNDS_LATEST] }
  });
}

export async function getRoundFreshness(): Promise<RoundFreshness> {
  return fetchJson<RoundFreshness>("/api/v1/rounds/freshness", {
    next: { revalidate: REVALIDATE_LATEST, tags: [TAG_ROUNDS_LATEST] }
  });
}

export async function getHomeSummary(): Promise<HomeSummary> {
  return fetchJson<HomeSummary>("/api/v1/home", {
    next: { revalidate: REVALIDATE_LATEST }
  });
}

export async function getPublicIncidents(): Promise<PublicIncident[]> {
  return fetchJson<PublicIncident[]>("/api/v1/status/incidents", {
    next: { revalidate: REVALIDATE_LATEST }
  });
}

export type BallFrequency = RequiredApi<components["schemas"]["BallFrequencyDto"]>;
export type PatternBucket = RequiredApi<components["schemas"]["PatternBucketDto"]>;
export type CompanionPair = RequiredApi<components["schemas"]["CompanionPairDto"]>;
export type RangeDistribution = RequiredApi<components["schemas"]["RangeDistribution"]>;
export type FirstPrizeHistory = RequiredApi<components["schemas"]["FirstPrizeHistoryDto"]>;

export type RankedCombination = RequiredApi<components["schemas"]["RankedCombinationDto"]>;

export type FrequencyStatsResponse = RequiredApi<components["schemas"]["FrequencyStatsResponse"]>;

export type PatternStatsResponse = RequiredApi<components["schemas"]["PatternStatsResponse"]>;

export type CompanionStatsResponse = RequiredApi<components["schemas"]["CompanionStatsResponse"]>;

export type AnalysisResponse = RequiredApi<components["schemas"]["AnalysisResponse"]>;

export async function getFrequencyStats(): Promise<FrequencyStatsResponse> {
  return fetchJson<FrequencyStatsResponse>("/api/v1/stats/frequency", {
    next: { revalidate: REVALIDATE_STATS, tags: [TAG_STATS] }
  });
}

export async function getPatternStats(): Promise<PatternStatsResponse> {
  return fetchJson<PatternStatsResponse>("/api/v1/stats/patterns", {
    next: { revalidate: REVALIDATE_STATS, tags: [TAG_STATS] }
  });
}

export async function getCompanionStats(): Promise<CompanionStatsResponse> {
  return fetchJson<CompanionStatsResponse>("/api/v1/stats/companion", {
    next: { revalidate: REVALIDATE_STATS, tags: [TAG_STATS] }
  });
}
