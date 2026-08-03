// M-1: api.ts/community-api.ts/browser-api.ts/ops-client.ts 네 트랜스포트가 저마다
// `{ signal, ...init }` 또는 `{ ...init, signal }`로 타임아웃 signal과 호출자 signal을
// 스프레드 순서로만 합성했다 — 그 결과 뒤에 오는 쪽이 앞의 signal을 통째로 덮어써,
// 두 형태는 "호출자 signal이 5초 타임아웃을 무력화하는가"에서 서로 다르게 동작했다
// (api.ts/browser-api.ts는 무력화 가능, community-api.ts는 불가능). 현재 signal을
// 넘기는 호출자가 없어 실 버그는 아니지만 계약이 갈라져 있는 것 자체가 결함이다.
// AbortSignal.any로 실제 합성해 어느 쪽이 먼저 중단시키든(호출자 취소든 타임아웃이든)
// 요청이 끊기게 한다 — 스프레드 순서에 의존하지 않는다.
export function composeAbortSignal(timeoutMs: number, callerSignal?: AbortSignal | null): AbortSignal {
  const signals = [AbortSignal.timeout(timeoutMs)];
  if (callerSignal) signals.push(callerSignal);
  return AbortSignal.any(signals);
}
