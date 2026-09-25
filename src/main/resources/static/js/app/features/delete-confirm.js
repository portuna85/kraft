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
// 모달을 열 때마다 올린다. 확인 버튼을 누른 시점의 값을 스냅샷 떠 두면, 그 요청이 끝났을 때
// 사용자가 이미 모달을 닫고 다른 대상을 열었는지(세대가 바뀌었는지) 구분할 수 있다 — 실제
// 삭제 자체는 kind/id를 따로 스냅샷 떠 두므로 항상 맞는 대상에 실행되지만, "지금 화면에 보이는
// 모달을 닫아도 되는가"는 이 세대로만 판단해야 한다(개선 보고서 F10). open()이 module 스코프
// 함수라 pending과 달리 init() 밖에 둔다.
let generation = 0;

export function init() {
    let pending = null; // { kind: 'post' | 'comment', id, trigger }

    delegate('click', '[data-target-kind="post"]', (trigger) => {
        pending = { kind: 'post', id: valueOf(byId('id')), trigger };
        open('post', trigger.dataset.targetName);
    });

    delegate('click', '[data-target-kind="comment"]', (trigger) => {
        const item = trigger.closest('.comment-list__item');
        pending = { kind: 'comment', id: item?.dataset.commentId, trigger };
        open('comment', trigger.dataset.targetName);
    });

    on(byId('confirmDeleteModal'), 'hidden.bs.modal', () => {
        // 모달을 닫으면 원래 눌렀던 버튼으로 돌아가야 키보드 사용자가 위치를 잃지 않는다.
        // 댓글 삭제 성공 경로는 모달이 완전히 닫히기 전에 kraft:comment-deleted 이벤트로
        // 그 댓글이 목록에서 이미 지워져, 이 시점에는 trigger가 DOM에 없다(F09) — 사라진
        // 요소에 focus()는 조용히 무시되어 포커스가 body로 떨어진다. 트리거가 아직 있으면
        // 그대로 돌아가고, 없으면(삭제 성공) 늘 존재하는 댓글 영역 제목으로 옮긴다.
        if (pending?.trigger && document.contains(pending.trigger)) {
            pending.trigger.focus();
        } else {
            byId('comments-heading')?.focus();
        }
        pending = null;
    });

    on(byId('btn-confirm-delete'), 'click', async () => {
        const button = byId('btn-confirm-delete');
        if (!pending || button.disabled) {
            return;
        }

        const { kind, id } = pending;
        const openedAt = generation;
        button.disabled = true;

        try {
            await api.del(kind === 'post' ? `${API.POSTS}/${id}` : `${API.COMMENTS}/${id}`);
            // 서버 반영은 이미 끝났다 — 안내와 목록 갱신은 세대와 무관하게 항상 수행한다.
            if (kind === 'post') {
                flash.set('POST_DELETED');
                window.location.href = '/';
            } else {
                // 댓글 목록은 Vue 아일랜드(src/vue/comments)가 그리므로 새로고침하지 않는다.
                // 이동이 없으니 showNow로 즉시 배너를 띄우고, 목록 갱신은 이벤트로 알린다.
                flash.showNow('COMMENT_DELETED');
                window.dispatchEvent(new CustomEvent('kraft:comment-deleted', { detail: { id } }));
            }
            // 모달을 닫는 것은 "지금 화면" 얘기다 — 그사이 사용자가 닫고 다른 대상을 열었다면
            // (세대가 바뀌었다면) 그 새 대화상자를 건드리지 않는다.
            if (openedAt === generation) {
                modal('#confirmDeleteModal').hide();
            }
        } catch (error) {
            // 되돌아갈 폼이 없는 배경 동작이라 토스트로 알린다(ui/flash.js의 규칙 참고).
            showToast(messageOf(error), 'danger');
            if (openedAt === generation) {
                modal('#confirmDeleteModal').hide();
            }
        } finally {
            // 이 버튼은 세대가 바뀌면 open()이 곧바로 다시 활성화한다(다른 대상은 곧바로
            // 확인할 수 있어야 한다) — 낡은 세대의 완료가 그 뒤 새로 시작된 요청의 disabled를
            // 도로 풀어버리면 안 된다.
            if (openedAt === generation) {
                button.disabled = false;
            }
        }
    });
}

// 삭제 확인 문구에 대상을 명시한다(문서 5.2) — 제목·댓글 내용이 길면 모달이 한눈에 안
// 들어오므로 자른다. 게시글 제목은 최대 255자, 댓글 내용은 최대 1000자라 그대로 넣으면
// 문구가 지나치게 길어질 수 있다.
function truncate(text, max) {
    if (!text) {
        return '';
    }
    return text.length > max ? `${text.slice(0, max)}…` : text;
}

function open(kind, targetName) {
    generation += 1;
    // 이전 대상의 요청이 아직 진행 중이더라도, 서로 다른 대상이면 동시에 처리해도 무방하다 —
    // 새로 연 대화상자는 그 요청과 독립적으로 곧바로 확인할 수 있어야 한다.
    byId('btn-confirm-delete').disabled = false;
    const isPost = kind === 'post';
    const name = truncate(targetName, isPost ? 40 : 30);
    setText(byId('confirmDeleteModalLabel'), isPost ? '게시글 삭제' : '댓글 삭제');
    setText(byId('confirmDeleteMessage'),
        isPost ? `게시글 "${name}"을(를) 삭제하시겠습니까?` : `댓글 "${name}"을(를) 삭제하시겠습니까?`);
    modal('#confirmDeleteModal').show();
}
