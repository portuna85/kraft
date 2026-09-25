// @ts-check

/**
 * Vue 아일랜드가 서버에서 받는 데이터의 모양(평가 보고서 2026-09-25 F09). 필드는 서버 DTO와
 * 맞춘다 — 이름을 바꾸면 이 파일도 함께 고쳐야 vue-tsc가 화면 쪽 불일치를 잡는다.
 * 런타임 코드는 없다. JSDoc에서 {@code import('../shared/types.js').CommentViewDto}처럼 참조한다.
 */

/**
 * src/main/java/com/kraft/comment/dto/CommentViewDto.java
 *
 * @typedef {Object} CommentViewDto
 * @property {number} id
 * @property {number} postId
 * @property {number | null} parentId
 * @property {string} content
 * @property {string} author
 * @property {string} createdAt 오프셋이 붙은 ISO-8601
 * @property {boolean} canManage
 * @property {CommentViewDto[]} replies 최상위 댓글이면 처음 로드된 답글 일부, 답글이면 빈 배열
 * @property {number} replyCount 서버 기준 전체 답글 수
 * @property {boolean} hasMoreReplies
 * @property {number} version 수정 요청에 그대로 돌려보내는 낙관적 잠금 버전
 * @property {number | string | null} [replyCursor] 화면 전용: 서버 페이지로 마지막에 받은 답글 id(commentState.js)
 */

/**
 * src/main/java/com/kraft/comment/dto/CommentPageDto.java — 댓글 목록 API와
 * #comments-initial-data가 공유하는 모양.
 *
 * @typedef {Object} CommentPageDto
 * @property {CommentViewDto[]} comments
 * @property {number} totalCount
 * @property {boolean} hasMore
 */

/**
 * src/main/java/com/kraft/post/dto/CategoryOptionDto.java
 *
 * @typedef {Object} CategoryOption
 * @property {string} value Category.name()
 * @property {string} title
 */

/**
 * src/main/java/com/kraft/post/dto/PostViewDto.java
 *
 * @typedef {Object} PostViewDto
 * @property {number} id
 * @property {string} title
 * @property {string} content
 * @property {string | null} picture
 * @property {string | null} author
 * @property {boolean} canManagePost
 * @property {string} category
 * @property {number} viewCount
 * @property {number} likeCount
 * @property {boolean} likedByMe
 * @property {number} version
 */

export {};
