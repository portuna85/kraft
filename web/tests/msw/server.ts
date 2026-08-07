import { setupServer } from "msw/node";

import { handlers } from "./handlers";

/**
 * feature 통합 테스트용 목 서버. 개별 테스트가 `server.use(...)`로 시나리오별 응답을
 * 덮어쓴다. 전역 setup에 넣지 않는 이유는, 목이 필요 없는 단위 테스트까지 네트워크
 * 레이어를 갈아끼우면 실패 원인이 흐려지기 때문이다 — 쓰는 테스트에서만 켠다.
 */
export const server = setupServer(...handlers);
