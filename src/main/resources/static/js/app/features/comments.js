import { byId, delegate, on, qs, setBusy, setHidden, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API } from '../core/constants.js';
import { showToast } from '../ui/toast.js';
import * as flash from '../ui/flash.js';

/**
 * 댓글 등록과 인라인 수정.
 *
 * 목록의 각 항목은 읽기 상태(.comment-view)와 편집 폼(.comment-edit-form)을 함께 갖고 있고
 * hidden 속성으로 둘 중 하나만 보여준다. 항목이 여러 개이고 다시 그려질 수 있어 버튼마다
 * 핸들러를 걸지 않고 document 위임을 쓴다 — 위임이 깨지면 버튼이 조용히 죽으므로 E2E로 고정해 두었다.
 *
 * 실패 안내가 토스트인 것은 의도된 예외다. 댓글 폼은 본문 아래쪽에 있고 #flash는 화면 맨
 * 위라, 배너를 띄우면 사용자가 보고 있지 않은 곳에 뜬다(ui/flash.js의 규칙 참고).
 */
export function init() {
    on(byId('btn-comment-save'), 'click', save);

    delegate('click', '.btn-comment-edit', (button) => {
        const item = button.closest('.comment-list__item');
        setHidden(qs('.comment-view', item), true);
        const form = qs('.comment-edit-form', item);
        setHidden(form, false);
        qs('textarea', form).focus();
    });

    delegate('click', '.btn-comment-cancel', (button) => {
        const item = button.closest('.comment-list__item');
        setHidden(qs('.comment-edit-form', item), true);
        setHidden(qs('.comment-view', item), false);
    });

    delegate('submit', '.comment-edit-form', (form, event) => {
        event.preventDefault();
        update(form);
    });
}

async function save() {
    const button = byId('btn-comment-save');
    setBusy(button, true);

    try {
        await api.post(`${API.POSTS}/${valueOf(byId('comment-post-id'))}/comments`, {
            content: valueOf(byId('comment-content')),
        });
        flash.set('COMMENT_SAVED');
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        setBusy(button, false);
    }
}

async function update(form) {
    const item = form.closest('.comment-list__item');
    const button = qs('.btn-comment-save', form);
    setBusy(button, true);

    try {
        await api.put(`${API.COMMENTS}/${item.dataset.commentId}`, {
            content: qs('textarea', form).value,
        });
        flash.set('COMMENT_UPDATED');
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        setBusy(button, false);
    }
}
