import pino from "pino";
import { join } from "path";
import createStream from "pino-rotating-file-stream";
import { mkdirSync } from "fs";
import { PHASE_PRODUCTION_BUILD } from "next/constants";

const isDev = process.env.NODE_ENV !== "production";

const logDir = process.env.KRAFT_LOG_PATH
  ? join(process.env.KRAFT_LOG_PATH, "web")
  : join(process.cwd(), "logs", "web");

type SerializedError = {
  type: string;
  name?: string;
  message: string;
  stack?: string;
  code?: string | number;
  cause?: SerializedError;
};

/**
 * Error와 DOMException의 진단 필드만 남긴다. DOMException에는 브라우저 오류 코드
 * 상수가 열거 가능한 속성으로 다수 노출될 수 있어 객체 전체를 직렬화하지 않는다.
 */
export function serializeError(error: unknown, includeCause = true): SerializedError {
  const isError =
    error instanceof Error ||
    (typeof DOMException !== "undefined" && error instanceof DOMException);
  if (!isError) {
    const constructorName =
      typeof error === "object" && error !== null
        ? error.constructor?.name
        : undefined;
    return {
      type: constructorName || typeof error,
      message:
        typeof error === "string" ||
        typeof error === "number" ||
        typeof error === "boolean"
          ? String(error)
          : "Non-Error value thrown",
    };
  }

  const errorLike = error as Error;
  const serialized: SerializedError = {
    type: errorLike.constructor.name || "Error",
    message: errorLike.message,
  };
  if (errorLike.name && errorLike.name !== serialized.type) {
    serialized.name = errorLike.name;
  }
  if (errorLike.stack) {
    serialized.stack = errorLike.stack;
  }

  const code = Reflect.get(errorLike, "code");
  if (typeof code === "string" || typeof code === "number") {
    serialized.code = code;
  }
  const cause = Reflect.get(errorLike, "cause");
  if (includeCause && cause !== undefined && cause !== error) {
    serialized.cause = serializeError(cause, false);
  }
  return serialized;
}

function buildStream() {
  if (isDev) return process.stdout;

  try {
    mkdirSync(logDir, { recursive: true });
    const rotating = createStream({
      filename: "web.log",
      interval: "1d",
      rotate: 30,
      size: "10M",
      maxSize: "200M",
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
    serializers: {
      err: serializeError,
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
