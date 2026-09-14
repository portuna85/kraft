import { byId, delegate, on, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { modal } from '../core/bootstrap-ui.js';
import { API } from '../core/constants.js';
import { showToast } from '../ui/toast.js';
import * as flash from '../ui/flash.js';

/**
 * 게시글과 댓글이 함께 쓰는 삭제 확인 모달. 호출한 버튼을 기억해 두었다가 확인을 누르면 그
 * 대상에 맞는 삭제를 수행하고, 모달을 닫으면 호출 버튼으로 포커스를 되돌린다.
 *
 * 댓글 목록은 다시 그려질 수 있어 버튼에 직접 걸지 않고 document 위임을 쓴다.
 */
export function init() {
    let pending = null; // { kind: 'post' | 'comment', id, trigger }

    delegate('click', '[data-target-kind="post"]', (trigger) => {
        pending = { kind: 'post', id: valueOf(byId('id')), trigger };
        open('post');
    });

    delegate('click', '[data-target-kind="comment"]', (trigger) => {
        const item = trigger.closest('.comment-list__item');
        pending = { kind: 'comment', id: item?.dataset.commentId, trigger };
        open('comment');
    });

    on(byId('confirmDeleteModal'), 'hidden.bs.modal', () => {
        // 모달을 닫으면 원래 눌렀던 버튼으로 돌아가야 키보드 사용자가 위치를 잃지 않는다.
        pending?.trigger?.focus();
        pending = null;
    });

    on(byId('btn-confirm-delete'), 'click', async () => {
        const button = byId('btn-confirm-delete');
        if (!pending || button.disabled) {
            return;
        }

        const { kind, id } = pending;
        button.disabled = true;

        try {
            await api.del(kind === 'post' ? `${API.POSTS}/${id}` : `${API.COMMENTS}/${id}`);
            modal('#confirmDeleteModal').hide();
            if (kind === 'post') {
                flash.set('POST_DELETED');
                window.location.href = '/';
            } else {
                // 댓글 목록은 Vue 아일랜드(src/vue/comments)가 그리므로 새로고침하지 않는다.
                // 이동이 없으니 showNow로 즉시 배너를 띄우고, 목록 갱신은 이벤트로 알린다.
                flash.showNow('COMMENT_DELETED');
                window.dispatchEvent(new CustomEvent('kraft:comment-deleted', { detail: { id } }));
            }
        } catch (error) {
            modal('#confirmDeleteModal').hide();
            // 되돌아갈 폼이 없는 배경 동작이라 토스트로 알린다(ui/flash.js의 규칙 참고).
            showToast(messageOf(error), 'danger');
        } finally {
            button.disabled = false;
        }
    });
}

function open(kind) {
    const isPost = kind === 'post';
    setText(byId('confirmDeleteModalLabel'), isPost ? '게시글 삭제' : '댓글 삭제');
    setText(byId('confirmDeleteMessage'), isPost ? '이 게시글을 삭제하시겠습니까?' : '이 댓글을 삭제하시겠습니까?');
    modal('#confirmDeleteModal').show();
}
