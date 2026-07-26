import pino from "pino";
import { join } from "path";
import createStream from "pino-rotating-file-stream";
import { mkdirSync } from "fs";
import { PHASE_PRODUCTION_BUILD } from "next/constants";

const isDev = process.env.NODE_ENV !== "production";

const logDir = process.env.KRAFT_LOG_PATH
  ? join(process.env.KRAFT_LOG_PATH, "web")
  : join(process.cwd(), "logs", "web");

function buildStream() {
  if (isDev) return process.stdout;

  try {
    mkdirSync(logDir, { recursive: true });
    const rotating = createStream({
      filename: "web.log",
      interval: "1d",
      rotate: 30,
      path: logDir,
      compress: "gzip",
    });
    return pino.multistream([{ stream: process.stdout }, { stream: rotating }]);
  } catch {
    return process.stdout;
  }
}

const logger = pino(
  {
    level: isDev ? "debug" : "info",
    timestamp: pino.stdTimeFunctions.isoTime,
    base: { service: "kraft-web" },
    formatters: {
      level(label) {
        return { level: label.toUpperCase() };
      },
    },
  },
  buildStream()
);

/**
 * 페이지의 핵심 데이터 조회 실패를 로깅한다. 호출자는 로깅 후 그대로 throw해
 * error.tsx(5xx)로 넘겨야 한다(로깅 자체는 여기서 끝나고 흐름 제어는 호출자 책임).
 *
 * `next build`의 정적 생성 단계(NEXT_PHASE=phase-production-build)에서는 백엔드가
 * 아예 없는 게 의도된 전제라 이 실패가 사고가 아니다 — 여기서 error 대신 warn으로
 * 남겨, 오프라인 빌드 로그를 실제 운영 사고 로그와 구분되게 한다. 런타임
 * (phase-production-server) 장애는 그대로 error 레벨을 유지한다.
 */
export function logCoreDataFailure(error: unknown, message: string): void {
  if (process.env.NEXT_PHASE === PHASE_PRODUCTION_BUILD) {
    logger.warn({ err: error }, `[빌드 타임 폴백] ${message} — 백엔드 없는 오프라인 빌드에서는 예상된 동작`);
  } else {
    logger.error({ err: error }, message);
  }
}

export default logger;
