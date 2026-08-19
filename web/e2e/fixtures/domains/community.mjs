// 커뮤니티(게시글·댓글·세션) 도메인 픽스처 — L-01로 backend.mjs에서 분리.

const POST_DETAIL = {
  id: 1,
  ownerId: 10,
  authorNickname: "테스터",
  title: "첫 글입니다",
  content: "본문 첫 줄\n본문 둘째 줄",
  category: "GENERAL",
  status: "PUBLISHED",
  version: 0,
  createdAt: "2026-08-01T12:00:00Z",
  updatedAt: "2026-08-01T12:00:00Z",
  likeCount: 3,
  commentCount: 2,
  viewCount: 41,
  recommendationAttachment: null,
};

// H-03: 추천 세트가 첨부된 게시글 — 상세 페이지의 RecommendationAttachmentView
// 렌더를 e2e로 확인하려면 실제로 attachment가 채워진 게시글이 필요하다.
const POST_WITH_ATTACHMENT = {
  ...POST_DETAIL,
  id: 2,
  title: "추천 세트를 첨부한 글",
  recommendationAttachment: {
    setId: 200,
    strategy: "balanced",
    algorithmVersion: "e2e-fixture",
    historyThroughRound: 1150,
    exclusionPolicyVersion: "e2e-fixture",
    items: [{ position: 0, numbers: [1, 8, 17, 24, 33, 41], score: null, explanationCodes: [] }],
  },
};

// 답글이 상위 댓글 안에 들어 있고, tombstone 댓글이 자리를 지키는지 확인하기 위한 형태다.
const COMMENT_PAGE = {
  topLevel: [
    {
      id: 11,
      postId: 1,
      parentId: null,
      ownerId: 10,
      authorNickname: "댓글쓴이",
      content: "첫 댓글",
      deleted: false,
      createdAt: "2026-08-01T13:00:00Z",
      targetPage: null,
      replies: [
        {
          id: 12,
          postId: 1,
          parentId: 11,
          ownerId: 20,
          authorNickname: "답글쓴이",
          content: "답글입니다",
          deleted: false,
          createdAt: "2026-08-01T13:30:00Z",
          targetPage: null,
          replies: [],
        },
      ],
    },
    {
      id: 13,
      postId: 1,
      parentId: null,
      ownerId: null,
      authorNickname: "(삭제됨)",
      content: "삭제된 댓글입니다.",
      deleted: true,
      createdAt: "2026-08-01T14:00:00Z",
      targetPage: null,
      replies: [],
    },
  ],
  totalTopLevelComments: 2,
  page: 0,
  size: 50,
  totalPages: 1,
};

// KF-01(docs/improvement.md): 로그인 상태를 테스트가 제어할 수 있어야 "로그인 생성 →
// 계정 이력 미반영" 결함을 재현할 수 있다. 기본은 비로그인이고, `setSessionState`로
// backend.mjs의 `__test__/session` 컨트롤 엔드포인트가 이 상태를 바꾼다.
const DEFAULT_SESSION_STATE = {
  loggedIn: false,
  userId: null,
  nickname: null,
  activeProviders: ["google", "naver"],
};
let sessionState = { ...DEFAULT_SESSION_STATE };

// 계정 스코프 추천 이력 저장소. KF-01(docs/improvement.md) 수정 후 실제 백엔드가
// `/api/v1/community/me/recommendation-sets`(POST)로 로그인 계정 소유 생성을
// 지원하므로, 이 픽스처도 그 쓰기 경로를 흉내낸다 — `/api/v1/numbers/recommend`
// (device-token, 익명 전용)와는 별개 저장소다.
let nextAccountSetId = 300;
const accountRecommendationSets = [];

export function setSessionState(next) {
  sessionState = { ...sessionState, ...next };
}

export function resetCommunityState() {
  sessionState = { ...DEFAULT_SESSION_STATE };
  accountRecommendationSets.length = 0;
}

export const routes = [
  ["/api/v1/community/session", () => sessionState],
  [
    "/api/v1/community/me/recommendation-sets",
    (_params, requestBody, method) => {
      if (method === "GET") {
        return {
          items: accountRecommendationSets,
          page: 0,
          size: 20,
          totalElements: accountRecommendationSets.length,
          totalPages: accountRecommendationSets.length === 0 ? 0 : 1,
        };
      }
      // POST — KF-01: 로그인 계정 소유로 생성한다. `/api/v1/numbers/recommend`
      // (numbers.mjs)의 응답 모양과 동일해야 recommendNumbersSchema를 통과한다.
      const strategy = requestBody?.strategy ?? "random";
      const items = [
        { position: 0, numbers: [2, 9, 18, 25, 34, 42], score: null, explanationCodes: [] },
        { position: 1, numbers: [4, 13, 20, 27, 36, 45], score: null, explanationCodes: [] },
      ];
      const createdAt = "2026-08-01T16:00:00Z";
      const setId = nextAccountSetId++;
      accountRecommendationSets.unshift({
        id: setId,
        strategy,
        algorithmVersion: "e2e-fixture",
        historyThroughRound: 1150,
        exclusionPolicyVersion: "e2e-fixture",
        lockedNumbers: requestBody?.lockedNumbers ?? [],
        excludedNumbers: requestBody?.excludedNumbers ?? [],
        createdAt,
        items,
      });
      return {
        recommendations: items.map((item) => item.numbers),
        strategy,
        algorithmVersion: "e2e-fixture",
        historyThroughRound: 1150,
        historicalExclusionApplied: false,
        exclusionPolicyVersion: "e2e-fixture",
        setId,
        items,
        createdAt,
      };
    },
  ],
  [
    "/api/v1/community/posts",
    // 기본은 목록이 1페이지뿐인 상태를 흉내낸다 — "총 N건" 표시(§12.8)를 e2e로
    // 확인하려면 빈 목록이 아니라 실제로 1페이지짜리 결과가 있어야 한다.
    //
    // category=WIN_STORY로 요청하면 대신 3페이지짜리 목록을 흉내낸다 — 페이지네이션
    // 컨트롤(처음·이전·다음·마지막, FE-052)을 e2e로 확인하려면 totalPages가 1보다
    // 커야 하는데, 그러면서도 기본 요청(파라미터 없음)에 기대는 기존 "총 N건" 테스트는
    // 그대로 둬야 해서 별도 카테고리로 분기했다.
    (searchParams) => {
      if (searchParams.get("category") !== "WIN_STORY") {
        return { items: [POST_DETAIL], page: 0, size: 20, totalElements: 1, totalPages: 1 };
      }
      const page = Number(searchParams.get("page") ?? "0");
      return {
        items: [
          { ...POST_DETAIL, id: page * 2 + 1, title: `당첨 후기 ${page * 2 + 1}` },
          { ...POST_DETAIL, id: page * 2 + 2, title: `당첨 후기 ${page * 2 + 2}` },
        ],
        page,
        size: 2,
        totalElements: 6,
        totalPages: 3,
      };
    },
  ],
  ["/api/v1/community/posts/1", POST_DETAIL],
  ["/api/v1/community/posts/2", POST_WITH_ATTACHMENT],
  [
    "/api/v1/community/posts/2/comments",
    () => ({ topLevel: [], totalTopLevelComments: 0, page: 0, size: 50, totalPages: 1 }),
  ],
  [
    "/api/v1/community/posts/1/comments",
    (_params, requestBody, method) => {
      if (method !== "POST") return COMMENT_PAGE;
      return {
        id: 14,
        postId: 1,
        parentId: requestBody?.parentId ?? null,
        ownerId: 10,
        authorNickname: "테스터",
        content: requestBody?.content ?? "",
        deleted: false,
        createdAt: "2026-08-01T15:30:00Z",
        targetPage: null,
        replies: [],
      };
    },
  ],
];

/**
 * 경로 파라미터가 있는 라우트 — ROUTES Map은 정확히 일치하는 경로만 찾으므로
 * `/api/v1/community/comments/:id` 같은 삭제 경로는 따로 정규식으로 처리한다.
 */
export const dynamicRoutes = [
  {
    pattern: /^\/api\/v1\/community\/comments\/(\d+)$/,
    method: "DELETE",
    handle: () => ({ status: 204, body: null }),
  },
];
