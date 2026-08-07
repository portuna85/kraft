import type { RequestHandler } from "msw";

/**
 * MSW 핸들러 — improvement_fe.md §21.7
 *
 * 핸들러 응답은 각 entity의 Valibot 스키마를 만족하는 값으로만 만든다. 목이 실제
 * 계약과 어긋나면 통합 테스트가 "지나가는데 프로덕션에서 터지는" 가짜 안전망이 된다.
 *
 * entities가 생기는 Phase 4부터 채워진다.
 */
export const handlers: RequestHandler[] = [];
