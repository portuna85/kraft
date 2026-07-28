// AUTO-GENERATED — do not edit by hand.
// Source: http://localhost:8080/v3/api-docs
// Regenerate: npm run generate:api-types (backend must be running locally)

export interface paths {
    "/api/v1/community/users/{id}/block": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["block"];
        post?: never;
        delete: operations["unblock"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/posts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail"];
        put: operations["update"];
        post?: never;
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/posts/{id}/like": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["like"];
        post?: never;
        delete: operations["unlike"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/posts/{id}/bookmark": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["bookmark"];
        post?: never;
        delete: operations["unbookmark"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/stats/analysis": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["analysis"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/saved": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post: operations["save"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/numbers/recommend": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["recommend"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["report"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/posts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_1"];
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/posts/{postId}/comments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_2"];
        put?: never;
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/status": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["status"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/status/incidents": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["incidents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/stats/patterns": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["patterns"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/stats/frequency": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["frequency"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/stats/companion": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["companion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/saved/matches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["matches"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/rounds/latest": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["latest"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/rounds/freshness": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["freshness"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/recommendation-sets": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/recommendation-sets/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["get"];
        put?: never;
        post?: never;
        delete: operations["delete_1"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/numbers/check": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["check"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/home": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["home"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/session": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["session"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/me/interactions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["interactions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/saved/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete: operations["delete_2"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/community/comments/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete: operations["delete_3"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        UpdatePostRequest: {
            title: string;
            content: string;
            /** Format: int64 */
            expectedVersion: number;
        };
        CommunityPostResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            ownerId?: number;
            authorNickname?: string;
            title?: string;
            content?: string;
            /** @enum {string} */
            category?: "RECOMMENDATION_SHARE" | "ROUND_ANALYSIS" | "WIN_STORY" | "QUESTION" | "GENERAL";
            /** @enum {string} */
            status?: "PUBLISHED" | "HIDDEN_BY_AUTHOR" | "HIDDEN_BY_MODERATOR" | "DELETED";
            /** Format: int64 */
            version?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
            /** Format: int32 */
            likeCount?: number;
            /** Format: int32 */
            commentCount?: number;
            /** Format: int64 */
            viewCount?: number;
            recommendationAttachment?: components["schemas"]["RecommendationAttachmentView"];
        };
        RecommendationAttachmentView: {
            /** Format: int64 */
            setId?: number;
            strategy?: string;
            algorithmVersion?: string;
            /** Format: int32 */
            historyThroughRound?: number;
            items?: components["schemas"]["RecommendationItemView"][];
        };
        RecommendationItemView: {
            /** Format: int32 */
            position?: number;
            numbers?: number[];
            /** Format: int32 */
            score?: number;
            explanationCodes?: ("ODD_EVEN_BALANCED" | "LOW_HIGH_BALANCED" | "SUM_IN_RANGE" | "CONSECUTIVE_PAIR_LIMITED" | "DECADE_SPREAD")[];
        };
        AnalysisRequest: {
            numbers: number[];
        };
        AnalysisResponse: {
            numbers?: number[];
            /** Format: int32 */
            oddCount?: number;
            /** Format: int32 */
            evenCount?: number;
            /** Format: int32 */
            lowCount?: number;
            /** Format: int32 */
            highCount?: number;
            /** Format: int32 */
            sumOfNumbers?: number;
            sumBucket?: string;
            /** Format: int32 */
            consecutivePairCount?: number;
            rangeDistribution?: components["schemas"]["RangeDistribution"][];
        };
        RangeDistribution: {
            range?: string;
            /** Format: int32 */
            count?: number;
        };
        CreateSavedNumberRequest: {
            numbers: number[];
            label?: string;
            source?: string;
        };
        SaveNumberResult: {
            savedNumber?: components["schemas"]["SavedNumberResponse"];
            created?: boolean;
        };
        SavedNumberResponse: {
            /** Format: int64 */
            id?: number;
            numbers?: number[];
            label?: string;
            source?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        RecommendNumbersRequest: {
            /** Format: int32 */
            count?: number;
            excludedNumbers?: number[];
            reduceSharedWinnerRisk?: boolean;
            strategy?: string;
            lockedNumbers?: number[];
        };
        RecommendNumbersResponse: {
            recommendations?: number[][];
            strategy?: string;
            algorithmVersion?: string;
            /** Format: int32 */
            historyThroughRound?: number;
            /** Format: int64 */
            setId?: number;
            items?: components["schemas"]["RecommendationItemView"][];
            /** Format: date-time */
            createdAt?: string;
        };
        CreateReportRequest: {
            /** @enum {string} */
            targetType: "POST" | "COMMENT" | "USER";
            /** Format: int64 */
            targetId: number;
            /** @enum {string} */
            reason: "SPAM" | "COMMERCIAL_PROMOTION" | "HARASSMENT" | "PERSONAL_INFO" | "FAKE_WIN" | "GAMBLING_GLORIFICATION" | "OTHER";
        };
        CreatePostRequest: {
            title: string;
            content: string;
            category: string;
            /** Format: int64 */
            recommendationSetId?: number;
        };
        CreateCommentRequest: {
            content: string;
            /** Format: int64 */
            parentId?: number;
        };
        CommunityCommentResponse: {
            /** Format: int64 */
            id?: number;
            /** Format: int64 */
            postId?: number;
            /** Format: int64 */
            parentId?: number;
            /** Format: int64 */
            ownerId?: number;
            authorNickname?: string;
            content?: string;
            deleted?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: int32 */
            targetPage?: number;
            replies?: components["schemas"]["CommunityCommentResponse"][];
        };
        InfoStatusResponse: {
            service?: string;
            status?: string;
            timezone?: string;
            checkedAt?: string;
        };
        PublicIncidentResponse: {
            /** Format: int32 */
            round?: number;
            type?: string;
            resolved?: boolean;
            /** Format: date-time */
            occurredAt?: string;
            /** Format: int32 */
            occurrences?: number;
        };
        PatternBucketDto: {
            bucketKey?: string;
            /** Format: int32 */
            count?: number;
        };
        PatternStatsResponse: {
            /** Format: int32 */
            totalRounds?: number;
            oddCounts?: components["schemas"]["PatternBucketDto"][];
            highCounts?: components["schemas"]["PatternBucketDto"][];
            sumBuckets?: components["schemas"]["PatternBucketDto"][];
        };
        BallFrequencyDto: {
            /** Format: int32 */
            ballNumber?: number;
            /** Format: int32 */
            frequency?: number;
            /** Format: int32 */
            lastRound?: number;
        };
        FrequencyStatsResponse: {
            /** Format: int32 */
            totalRounds?: number;
            frequencies?: components["schemas"]["BallFrequencyDto"][];
            topSix?: components["schemas"]["RankedCombinationDto"];
            bottomSix?: components["schemas"]["RankedCombinationDto"];
        };
        RankedCombinationDto: {
            balls?: components["schemas"]["BallFrequencyDto"][];
            wonFirstPrize?: boolean;
        };
        CompanionPairDto: {
            /** Format: int32 */
            ballA?: number;
            /** Format: int32 */
            ballB?: number;
            /** Format: int32 */
            coCount?: number;
        };
        CompanionStatsResponse: {
            /** Format: int32 */
            totalRounds?: number;
            topPairs?: components["schemas"]["CompanionPairDto"][];
        };
        SavedNumberMatchResult: {
            savedNumber?: components["schemas"]["SavedNumberResponse"];
            /** Format: int32 */
            round?: number;
            /** Format: date */
            drawDate?: string;
            drawNumbers?: number[];
            /** Format: int32 */
            bonusNumber?: number;
            /** Format: int32 */
            matchedCount?: number;
            bonusMatch?: boolean;
            prizeTier?: string;
        };
        WinningNumberResponse: {
            /** Format: int32 */
            round?: number;
            /** Format: date */
            drawDate?: string;
            numbers?: number[];
            /** Format: int32 */
            bonusNumber?: number;
            /** Format: int64 */
            firstPrizeAmount?: number;
            /** Format: int64 */
            secondPrize?: number;
            /** Format: int32 */
            secondWinners?: number;
            /** Format: int64 */
            totalSales?: number;
            /** Format: int64 */
            firstAccumAmount?: number;
        };
        RoundFreshnessResponse: {
            /** Format: int32 */
            latestRound?: number;
            /** Format: date */
            latestDrawDate?: string;
            fresh?: boolean;
            /** Format: date-time */
            checkedAt?: string;
        };
        RecommendationSetSummary: {
            /** Format: int64 */
            id?: number;
            strategy?: string;
            algorithmVersion?: string;
            /** Format: int32 */
            historyThroughRound?: number;
            lockedNumbers?: number[];
            excludedNumbers?: number[];
            /** Format: date-time */
            createdAt?: string;
            items?: components["schemas"]["RecommendationItemView"][];
        };
        CombinationCheckResponse: {
            wonFirstPrize?: boolean;
        };
        HomeCommunityPostSummary: {
            /** Format: int64 */
            id?: number;
            title?: string;
            authorNameSnapshot?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        HomeResponse: {
            latestRound?: components["schemas"]["WinningNumberResponse"];
            freshness?: components["schemas"]["RoundFreshnessResponse"];
            latestPosts?: components["schemas"]["HomeCommunityPostSummary"][];
            weeklyPopularPosts?: components["schemas"]["HomeCommunityPostSummary"][];
        };
        CommunitySessionResponse: {
            loggedIn?: boolean;
            /** Format: int64 */
            userId?: number;
            nickname?: string;
            activeProviders?: string[];
        };
        PageResponseCommunityPostResponse: {
            items?: components["schemas"]["CommunityPostResponse"][];
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        CommunityCommentPageResponse: {
            topLevel?: components["schemas"]["CommunityCommentResponse"][];
            /** Format: int64 */
            totalTopLevelComments?: number;
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            /** Format: int32 */
            totalPages?: number;
        };
        CommunityInteractionsResponse: {
            likedPostIds?: number[];
            bookmarkedPostIds?: number[];
            blockedUserIds?: number[];
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
    block: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    unblock: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    detail: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                    "*/*": components["schemas"]["CommunityPostResponse"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdatePostRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CommunityPostResponse"];
                };
            };
        };
    };
    delete: {
        parameters: {
            query: {
                expectedVersion: number;
            };
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    like: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    unlike: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    bookmark: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    unbookmark: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    analysis: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnalysisRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AnalysisResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
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
                    "*/*": components["schemas"]["SavedNumberResponse"][];
                };
            };
        };
    };
    save: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSavedNumberRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SaveNumberResult"];
                };
            };
        };
    };
    recommend: {
        parameters: {
            query?: never;
            header?: {
                "X-Device-Token"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["RecommendNumbersRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["RecommendNumbersResponse"];
                };
            };
        };
    };
    report: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateReportRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    list_1: {
        parameters: {
            query?: {
                category?: string;
                sort?: string;
                query?: string;
                page?: number;
                size?: number;
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
                    "*/*": components["schemas"]["PageResponseCommunityPostResponse"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: {
                "X-Device-Token"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreatePostRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CommunityPostResponse"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
            };
            header?: never;
            path: {
                postId: number;
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
                    "*/*": components["schemas"]["CommunityCommentPageResponse"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                postId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateCommentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["CommunityCommentResponse"];
                };
            };
        };
    };
    status: {
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
                    "*/*": components["schemas"]["InfoStatusResponse"];
                };
            };
        };
    };
    incidents: {
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
                    "*/*": components["schemas"]["PublicIncidentResponse"][];
                };
            };
        };
    };
    patterns: {
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
                    "*/*": components["schemas"]["PatternStatsResponse"];
                };
            };
        };
    };
    frequency: {
        parameters: {
            query?: {
                limit?: number;
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
                    "*/*": components["schemas"]["FrequencyStatsResponse"];
                };
            };
        };
    };
    companion: {
        parameters: {
            query?: {
                ball?: number;
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
                    "*/*": components["schemas"]["CompanionStatsResponse"];
                };
            };
        };
    };
    matches: {
        parameters: {
            query?: {
                round?: string;
            };
            header: {
                "X-Device-Token": string;
            };
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
                    "*/*": components["schemas"]["SavedNumberMatchResult"][];
                };
            };
        };
    };
    latest: {
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
    freshness: {
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
                    "*/*": components["schemas"]["RoundFreshnessResponse"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
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
                    "*/*": components["schemas"]["RecommendationSetSummary"][];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
            path: {
                id: number;
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
                    "*/*": components["schemas"]["RecommendationSetSummary"];
                };
            };
        };
    };
    delete_1: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    check: {
        parameters: {
            query: {
                numbers: number[];
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
                    "*/*": components["schemas"]["CombinationCheckResponse"];
                };
            };
        };
    };
    home: {
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
                    "*/*": components["schemas"]["HomeResponse"];
                };
            };
        };
    };
    session: {
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
                    "*/*": components["schemas"]["CommunitySessionResponse"];
                };
            };
        };
    };
    interactions: {
        parameters: {
            query: {
                postIds: number[];
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
                    "*/*": components["schemas"]["CommunityInteractionsResponse"];
                };
            };
        };
    };
    delete_2: {
        parameters: {
            query?: never;
            header: {
                "X-Device-Token": string;
            };
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
    delete_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
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
                content?: never;
            };
        };
    };
}
