// @ts-check

/**
 * 댓글·답글 목록의 로컬 상태 변경 규칙(평가 보고서 2026-09-25 F02·F03). 화면(CommentsApp.vue·
 * CommentItem.vue)은 요청과 알림만 맡고, 목록·개수·커서를 어떻게 바꿀지는 여기서 정한다 —
 * 브라우저 없이 `node --test`로 조합 시나리오를 검사할 수 있게 순수 함수로 분리했다.
 * <p>
 * 인자로 받은 객체를 제자리에서 바꾼다. Vue의 reactive 프록시를 그대로 넘기면 화면이 따라 갱신된다.
 */

/**
 * @typedef {Object} ReplyLike
 * @property {number|string} id
 */

/**
 * @typedef {Object} ParentComment
 * @property {number|string} id
 * @property {ReplyLike[]} [replies]      화면에 로드된 답글(서버 페이지 + 이 화면에서 새로 쓴 답글)
 * @property {number} [replyCount]        서버 기준 이 부모의 전체 답글 수(로드 여부와 무관)
 * @property {boolean} [hasMoreReplies]   서버에 아직 받지 않은 답글이 있는지
 * @property {number|string|null} [replyCursor] 서버 페이지로 마지막에 받은 답글 id — "답글 더 보기"의 afterId
 */

const byId = (/** @type {ReplyLike} */ a, /** @type {ReplyLike} */ b) => Number(a.id) - Number(b.id);

/**
 * 서버가 내려준 초기 답글로 부모의 답글 커서를 잡는다. 이미 잡혀 있으면 두지 않는다.
 * <p>
 * 커서를 replies 배열에서 매번 다시 구하면 안 된다 — 이 화면에서 새로 쓴 답글(가장 큰 id)이
 * 배열 끝에 붙는 순간 커서가 그 id로 건너뛰어, 아직 받지 않은 그 사이 답글을 영영 못 받는다(F02).
 *
 * @param {ParentComment} parent
 */
export function initReplyCursor(parent) {
    if (parent.replyCursor !== undefined) {
        return;
    }
    const replies = parent.replies ?? [];
    parent.replyCursor = replies.length > 0 ? replies[replies.length - 1].id : null;
}

/**
 * "답글 더 보기" 요청에 쓸 afterId. 커서가 없으면 처음부터 받는다.
 *
 * @param {ParentComment} parent
 * @returns {string}
 */
export function replyAfterId(parent) {
    return parent.replyCursor == null ? '' : String(parent.replyCursor);
}

/**
 * 이 화면에서 등록에 성공한 답글을 부모에 붙인다. 커서는 움직이지 않는다(F02).
 *
 * @param {ParentComment} parent
 * @param {ReplyLike} reply
 * @returns {boolean} 새로 붙였으면 true(이미 있으면 개수를 다시 올리지 않는다)
 */
export function applyReplyCreated(parent, reply) {
    if (!parent.replies) {
        parent.replies = [];
    }
    if (parent.replies.some((r) => String(r.id) === String(reply.id))) {
        return false;
    }
    parent.replies.push(reply);
    parent.replies.sort(byId);
    parent.replyCount = (parent.replyCount ?? 0) + 1;
    return true;
}

/**
 * "답글 더 보기"로 받은 서버 페이지를 합친다. id로 중복을 거르고 오름차순을 유지하며, 이미
 * 삭제한 답글은 늦게 도착한 응답에 들어 있어도 되살리지 않는다. 커서는 서버 페이지로만 전진한다.
 *
 * @param {ParentComment} parent
 * @param {ReplyLike[]} page 서버가 id 오름차순으로 준 답글
 * @param {boolean} hasMore
 * @param {Set<string>} deletedIds
 */
export function applyRepliesPage(parent, page, hasMore, deletedIds) {
    if (!parent.replies) {
        parent.replies = [];
    }
    const existing = new Set(parent.replies.map((r) => String(r.id)));
    for (const reply of page) {
        const id = String(reply.id);
        if (!existing.has(id) && !deletedIds.has(id)) {
            parent.replies.push(reply);
            existing.add(id);
        }
    }
    parent.replies.sort(byId);
    if (page.length > 0) {
        const last = page[page.length - 1].id;
        if (parent.replyCursor == null || Number(last) > Number(parent.replyCursor)) {
            parent.replyCursor = last;
        }
    }
    parent.hasMoreReplies = hasMore;
}

/**
 * 삭제 완료를 목록에 반영하고, 전체 댓글 수에서 뺄 개수를 돌려준다.
 * <p>
 * 최상위 댓글이면 서버가 그 답글까지 지우므로 1 + 서버 기준 답글 수(replyCount)를 뺀다 — 일부만
 * 로드됐을 수 있는 replies.length가 아니다. 답글이면 부모의 replyCount도 함께 줄인다 — 그러지
 * 않으면 이어서 부모를 지울 때 이미 뺀 답글을 한 번 더 빼 개수가 음수가 됐다(F03).
 * 이미 반영한 삭제가 다시 오면 0을 돌려준다.
 *
 * @param {ParentComment[]} comments 최상위 댓글 목록
 * @param {number|string} id
 * @param {Set<string>} deletedIds 삭제한 댓글·답글 id(늦게 온 응답이 되살리지 않도록 기록)
 * @returns {number}
 */
export function applyDelete(comments, id, deletedIds) {
    const key = String(id);
    if (deletedIds.has(key)) {
        return 0;
    }
    const topIndex = comments.findIndex((c) => String(c.id) === key);
    if (topIndex !== -1) {
        const target = comments[topIndex];
        const removed = 1 + (target.replyCount ?? target.replies?.length ?? 0);
        comments.splice(topIndex, 1);
        deletedIds.add(key);
        return removed;
    }
    for (const parent of comments) {
        const replyIndex = parent.replies?.findIndex((r) => String(r.id) === key) ?? -1;
        if (replyIndex !== -1 && parent.replies) {
            parent.replies.splice(replyIndex, 1);
            if (typeof parent.replyCount === 'number') {
                parent.replyCount -= 1;
            }
            deletedIds.add(key);
            return 1;
        }
    }
    return 0;
}
