/**
 * 조회 한 건의 상태를 판별 유니온으로 표현한다.
 *
 * FE-036: `/companion`의 FilterState가 이 저장소에서 유일하게 "불가능한 상태 조합을
 * 타입 수준에서 배제"하고 있었다. 나머지 화면은 `isLoading`·`hasError`·`data|null`
 * 같은 불리언 플래그를 나란히 두어 `isLoading && hasError` 같은 조합이 컴파일에
 * 통과했고, 실제로 로딩 중인지 실패인지 빈 데이터인지 렌더 분기에서 매번 다시
 * 추론해야 했다(§12.1·§12.2의 혼재가 여기서 나온다).
 *
 * 상태 전이는 각 화면이 소유하고, 여기서는 "어떤 상태들이 존재하는가"만 고정한다.
 */
export type AsyncState<T> =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: T }
  | { status: "error" };

export const asyncIdle = { status: "idle" } as const;
export const asyncLoading = { status: "loading" } as const;
export const asyncError = { status: "error" } as const;

export function asyncSuccess<T>(data: T): AsyncState<T> {
  return { status: "success", data };
}

/** 성공 상태일 때만 데이터를 꺼낸다. 아직 로딩·실패면 undefined. */
export function asyncData<T>(state: AsyncState<T>): T | undefined {
  return state.status === "success" ? state.data : undefined;
}
