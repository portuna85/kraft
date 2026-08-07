import {
  REVALIDATE_LATEST,
  REVALIDATE_STATS,
  TAG_ROUNDS_LATEST,
  TAG_STATS,
} from "@/lib/revalidate";
import type { components } from "@/lib/generated/api-types";
import { backendBaseUrl } from "@/lib/backend-url";
import { composeAbortSignal } from "@/lib/transport-signal";

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

export type WinningNumber = components["schemas"]["WinningNumberResponse"];

export type RoundFreshness = components["schemas"]["RoundFreshnessResponse"];

export type PublicIncident = components["schemas"]["PublicIncidentResponse"];

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
  // W-1: SSR 렌더 무한 대기 방지. 호출자 signal이 있으면 둘 다 반영한다(M-1).
  const signal = composeAbortSignal(5000, init?.signal);
  const response = await fetch(`${backendBaseUrl}${path}`, { ...init, signal });
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

export async function getPublicIncidents(): Promise<PublicIncident[]> {
  return fetchJson<PublicIncident[]>("/api/v1/status/incidents", {
    next: { revalidate: REVALIDATE_LATEST }
  });
}

export type BallFrequency = components["schemas"]["BallFrequencyDto"];
export type PatternBucket = components["schemas"]["PatternBucketDto"];
export type CompanionPair = components["schemas"]["CompanionPairDto"];
export type RangeDistribution = components["schemas"]["RangeDistribution"];
export type FirstPrizeHistory = components["schemas"]["FirstPrizeHistoryDto"];

export type RankedCombination = components["schemas"]["RankedCombinationDto"];

export type FrequencyStatsResponse = components["schemas"]["FrequencyStatsResponse"];

export type PatternStatsResponse = components["schemas"]["PatternStatsResponse"];

export type CompanionStatsResponse = components["schemas"]["CompanionStatsResponse"];

export type AnalysisResponse = components["schemas"]["AnalysisResponse"];

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
