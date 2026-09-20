import { byId, delegate, on, setText, valueOf } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { modal } from '../core/bootstrap-ui.js';
import { API } from '../core/constants.js';
import { showToast } from '../ui/toast.js';

/**
 * 게시글과 댓글이 함께 쓰는 신고 모달. 삭제 확인 모달(delete-confirm.js)과 같은 구조다 —
 * 호출한 버튼을 기억해 두었다가 닫을 때 그 버튼으로 포커스를 돌려준다.
 *
 * 댓글 목록은 Vue가 다시 그리므로 버튼에 직접 걸지 않고 document 위임을 쓴다.
 *
 * 결과는 토스트로 알린다. 신고는 화면을 떠나지 않는 배경 동작이고, 사용자가 이어서 고칠 폼도
 * 없다(ui/flash.js의 규칙 참고).
 */
// delete-confirm.js와 같은 이유(개선 보고서 F10) — 요청이 끝났을 때 화면에 보이는 모달을
// 닫아도 되는지는 이 세대로만 판단한다. open()이 module 스코프 함수라 pending과 달리 init()
// 밖에 둔다.
let generation = 0;

export function init() {
    let pending = null; // { targetType: 'POST' | 'COMMENT', targetId, trigger }

    delegate('click', '[data-report-kind="post"]', (trigger) => {
        pending = { targetType: 'POST', targetId: valueOf(byId('id')), trigger };
        open('게시글');
    });

    delegate('click', '[data-report-kind="comment"]', (trigger) => {
        const item = trigger.closest('.comment-list__item');
        pending = { targetType: 'COMMENT', targetId: item?.dataset.commentId, trigger };
        open('댓글');
    });

    on(byId('reportModal'), 'hidden.bs.modal', () => {
        pending?.trigger?.focus();
        pending = null;
    });

    on(byId('report-form'), 'submit', async (event) => {
        event.preventDefault();
        if (!pending) {
            return;
        }

        const button = byId('btn-confirm-report');
        const openedAt = generation;
        button.disabled = true;
        try {
            await api.post(API.REPORTS, {
                targetType: pending.targetType,
                targetId: Number(pending.targetId),
                reason: valueOf(byId('report-reason')),
                detail: valueOf(byId('report-detail')),
            });
            showToast('신고가 접수되었습니다. 관리자가 확인합니다.', 'success');
            if (openedAt === generation) {
                modal('#reportModal').hide();
            }
        } catch (error) {
            // 이미 신고한 대상·자기 글처럼 사용자가 알아야 할 이유가 서버 문구에 들어 있다.
            showToast(messageOf(error), 'danger');
            if (openedAt === generation) {
                modal('#reportModal').hide();
            }
        } finally {
            // delete-confirm.js와 같은 이유 — 낡은 세대의 완료가 새로 시작된 요청의 disabled를
            // 도로 풀어버리면 안 된다.
            if (openedAt === generation) {
                button.disabled = false;
            }
        }
    });
}

function open(targetTitle) {
    generation += 1;
    byId('btn-confirm-report').disabled = false;
    byId('report-form').reset();
    setText(byId('reportModalLabel'), `${targetTitle} 신고`);
    setText(byId('report-target-hint'), `이 ${targetTitle}을(를) 신고합니다. 관리자가 확인한 뒤 처리합니다.`);
    modal('#reportModal').show();
}
