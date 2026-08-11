/** 진행 중인 액션 하나만 추적한다 — 여러 버튼이 동시에 눌리는 상황을 만들지 않는다. */
export type LoadingAction = "summary" | "collect-latest" | "collect-round" | "upsert" | null;
