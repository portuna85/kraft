// 자동 생성 파일 — 손으로 고치지 마세요.
// 원본: http://localhost:8080/v3/api-docs/ops
// 재생성: npm run generate:api-types (백엔드가 로컬에 떠 있어야 함)

export interface paths {
    "/ops/rounds": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["upsertRound"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ops/collect/{round}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["collectRound"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ops/collect/latest": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["collectLatest"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ops/summary": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["summary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/ops/logs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["logs"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        WinningNumberUpsertRequest: {
            /** Format: int32 */
            round: number;
            /** Format: date */
            drawDate: string;
            numbers: number[];
            /** Format: int32 */
            bonusNumber: number;
            /** Format: int64 */
            firstPrizeAmount: number;
            /** Format: int64 */
            secondPrize?: number;
            /** Format: int32 */
            secondWinners?: number;
            /** Format: int64 */
            totalSales?: number;
            /** Format: int64 */
            firstAccumAmount?: number;
        };
        WinningNumberResponse: {
            /** Format: int32 */
            round: number;
            /** Format: date */
            drawDate: string;
            numbers: number[];
            /** Format: int32 */
            bonusNumber: number;
            /** Format: int64 */
            firstPrizeAmount: number;
            /** Format: int64 */
            secondPrize: number;
            /** Format: int32 */
            secondWinners: number;
            /** Format: int64 */
            totalSales: number;
            /** Format: int64 */
            firstAccumAmount: number;
        };
        OpsSummaryResponse: {
            service: string;
            timezone: string;
            status: string;
            /** Format: int32 */
            latestRound: number | null;
            latestDrawDate: string | null;
            /** Format: date-time */
            checkedAt: string;
            fresh: boolean;
        };
        PageResponseWinningNumberOperationLogResponse: {
            items: components["schemas"]["WinningNumberOperationLogResponse"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            totalElements: number;
            /** Format: int32 */
            totalPages: number;
        };
        WinningNumberOperationLogResponse: {
            /** Format: int64 */
            id: number;
            operationType: string;
            executionStatus: string;
            /** Format: int32 */
            round: number | null;
            sourceDetail: string | null;
            message: string | null;
            requestId: string | null;
            /** Format: date-time */
            createdAt: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    upsertRound: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WinningNumberUpsertRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WinningNumberResponse"];
                };
            };
        };
    };
    collectRound: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                round: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WinningNumberResponse"];
                };
            };
        };
    };
    collectLatest: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["WinningNumberResponse"];
                };
            };
        };
    };
    summary: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["OpsSummaryResponse"];
                };
            };
        };
    };
    logs: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                operationType?: string;
                executionStatus?: string;
                round?: number;
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PageResponseWinningNumberOperationLogResponse"];
                };
            };
        };
    };
}
