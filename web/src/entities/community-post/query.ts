import { POST_CATEGORIES, POST_SORTS, type PostCategory, type PostSort } from "./schema";

/**
 * 목록 입력 방어, 레거시 M-11
 *
 * URL은 사용자가 직접 편집할 수 있는 입력이다. 여기서 거르지 않으면 백엔드가 400을
 * 돌려주고, 그 400은 화면에서 "검색 결과 없음"과 구분되지 않는 실패로 보인다.
 *
 * 이 함수들은 순수 함수라 단위 테스트로 전 경우를 고정할 수 있다(T-11·T-12).
 */

const MAX_PAGE = 100_000;

/**
 * `?page=Infinity`·`1.5`·`1e21`·`-3`을 전부 0으로 되돌린다.
 *
 * Number()만 쓰면 Infinity와 1e21이 그대로 통과해 백엔드로 나간다. 유한한 정수이면서
 * 상한 안에 있는 값만 받는다.
 */
export function parsePage(raw: string | undefined): number {
  if (raw === undefined) return 0;

  const value = Number(raw);
  if (!Number.isFinite(value)) return 0;
  if (!Number.isInteger(value)) return 0;
  if (value < 0 || value > MAX_PAGE) return 0;
  return value;
}

/**
 * 검색어는 **2~50자**여야 한다(백엔드 제약과 일치).
 * 짧으면 undefined를 돌려 아예 보내지 않는다 — 보내면 400이다.
 */
export function normalizeQuery(raw: string | undefined): string | undefined {
  if (raw === undefined) return undefined;

  const trimmed = raw.trim().slice(0, 50);
  return trimmed.length < 2 ? undefined : trimmed;
}

export function parseCategory(raw: string | undefined): PostCategory | undefined {
  return POST_CATEGORIES.find((category) => category === raw);
}

export function parseSort(raw: string | undefined): PostSort {
  return POST_SORTS.find((sort) => sort === raw) ?? "latest";
}

export type ListParams = {
  page: number;
  category: PostCategory | undefined;
  sort: PostSort;
  query: string | undefined;
};

export function parseListParams(searchParams: {
  page?: string;
  category?: string;
  sort?: string;
  query?: string;
}): ListParams {
  return {
    page: parsePage(searchParams.page),
    category: parseCategory(searchParams.category),
    sort: parseSort(searchParams.sort),
    query: normalizeQuery(searchParams.query),
  };
}

/** 필터가 하나라도 걸려 있는지 — 빈 결과의 원인을 구분하는 데 쓴다(FE-048). */
export function hasActiveFilter(params: ListParams): boolean {
  return params.category !== undefined || params.query !== undefined;
}

/** 현재 조건을 유지한 채 일부만 바꾼 링크를 만든다. 기본값은 URL에서 생략한다. */
export function buildListHref(params: ListParams, overrides: Partial<ListParams> = {}): string {
  const merged = { ...params, ...overrides };
  const search = new URLSearchParams();

  if (merged.page > 0) search.set("page", String(merged.page));
  if (merged.category !== undefined) search.set("category", merged.category);
  if (merged.sort !== "latest") search.set("sort", merged.sort);
  if (merged.query !== undefined) search.set("query", merged.query);

  const queryString = search.toString();
  return queryString === "" ? "/community" : `/community?${queryString}`;
}
