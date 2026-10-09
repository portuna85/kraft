import { byId, delegate, valueOf } from '../core/dom.js';
import { api } from '../core/http.js';
import { API } from '../core/constants.js';
import { truncate } from '../core/text.js';
import { confirmAction } from '../ui/confirm-dialog.js';
import * as flash from '../ui/flash.js';

/**
 * 게시글과 댓글이 함께 쓰는 삭제 확인. 모달·포커스 복귀·세대 판단은 공용 confirmAction이 맡고,
 * 이 모듈은 어떤 대상을 어떻게 지우는지만 안다.
 *
 * 댓글 목록은 다시 그려질 수 있어 버튼에 직접 걸지 않고 document 위임을 쓴다.
 */
export function init() {
    delegate('click', '[data-target-kind="post"]', (trigger) => {
        const id = valueOf(byId('post-id'));
        confirmDelete('post', id, trigger);
    });

    delegate('click', '[data-target-kind="comment"]', (trigger) => {
        const item = /** @type {HTMLElement | null} */ (trigger.closest('.comment-list__item'));
        confirmDelete('comment', item?.dataset.commentId, trigger);
    });
}

/**
 * @param {'post' | 'comment'} kind
 * @param {string | undefined} id
 * @param {HTMLElement} trigger
 */
function confirmDelete(kind, id, trigger) {
    const isPost = kind === 'post';
    const name = truncate(trigger.dataset.targetName, isPost ? 40 : 30);
    const label = isPost ? '게시글' : '댓글';
    confirmAction({
        title: `${label} 삭제`,
        message: `${label} "${name}"을(를) 삭제하시겠습니까?`,
        confirmLabel: '삭제',
        trigger,
        // 댓글 삭제 성공 경로는 모달이 닫히기 전에 그 댓글이 목록에서 지워져 trigger가 사라진다.
        fallbackFocusId: 'comments-heading',
        run: () => deleteTarget(kind, id),
    });
}

async function deleteTarget(kind, id) {
    const result = await api.del(kind === 'post' ? `${API.POSTS}/${id}` : `${API.COMMENTS}/${id}`);
    // 서버 반영은 이미 끝났다 — 안내와 목록 갱신은 세대와 무관하게 항상 수행한다.
    if (kind === 'post') {
        flash.set('POST_DELETED');
        window.location.href = '/community';
        return;
    }
    // 댓글 목록은 Vue 아일랜드(src/vue/comments)가 그리므로 새로고침하지 않는다. 이동이 없으니 showNow로 즉시 배너를 띄우고 목록 갱신은 이벤트로 알린다.
    // softDeleted면(답글이 남아 있어 행을 지우지 않음) 통째로 지우지 않고 "삭제된 댓글입니다"로 바꿔야 하므로 그 구분을 함께 실어 보낸다.
    flash.showNow('COMMENT_DELETED');
    window.dispatchEvent(new CustomEvent('kraft:comment-deleted', {
        detail: { id, softDeleted: result?.softDeleted ?? false },
    }));
}
