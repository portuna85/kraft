import { delegate, qs } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { showToast } from '../ui/toast.js';
import { confirmAction } from '../ui/confirm-dialog.js';

/**
 * 관리자 화면의 처리 버튼들 — 신고 목록의 삭제·반려와 정지 회원 목록의 해제.
 *
 * 처리에 성공하면 현재 페이지를 다시 불러온다. 로컬에서 줄만 지우면 "처리 대기" 카운트·
 * 페이지 수가 서버 상태와 어긋날 수 있다(F05) — post-edit/post-save가 성공 후
 * window.location.href로 전체 이동하는 것과 같은 관례다.
 *
 * 되돌릴 수 없는 삭제·삭제+정지는 첫 클릭에 바로 실행되지 않는다(개선 보고서 SEC-05·
 * FE-C1) — confirmAction으로 한 번 더 확인한다. 반려·정지 해제는 되돌릴 수 있으므로(다시
 * 신고하거나 다시 정지할 수 있음) 확인을 요구하지 않는다.
 */
export function init() {
    if (!qs('.report-list')) {
        return;
    }

    delegate('click', '.btn-lift-suspension', liftSuspension);
    delegate('click', '.btn-report-resolve', async (trigger) => {
        const { isPost, name } = targetOf(trigger);
        const confirmed = await confirmAction({
            title: isPost ? '게시글 삭제' : '댓글 삭제',
            message: name
                ? `${isPost ? '게시글' : '댓글'} "${name}"을(를) 삭제하시겠습니까? 되돌릴 수 없습니다.`
                // 대상이 이미 삭제된 신고는 targetName이 없다 — 일반 문구로 물러선다.
                : '이 신고 대상을 삭제하시겠습니까? 되돌릴 수 없습니다.',
            confirmLabel: '삭제',
        });
        if (confirmed) {
            handle(trigger, 'resolve');
        }
    });
    delegate('click', '.btn-report-suspend', async (trigger) => {
        const { isPost, name } = targetOf(trigger);
        const suspendDays = Number(trigger.dataset.suspendDays);
        const confirmed = await confirmAction({
            title: '삭제 + 정지',
            message: name
                ? `${isPost ? '게시글' : '댓글'} "${name}"을(를) 삭제하고 작성자를 ${suspendDays}일 정지하시겠습니까? 되돌릴 수 없습니다.`
                : `이 신고 대상을 삭제하고 작성자를 ${suspendDays}일 정지하시겠습니까? 되돌릴 수 없습니다.`,
            confirmLabel: '삭제 + 정지',
        });
        if (confirmed) {
            handle(trigger, 'resolve', suspendDays);
        }
    });
    delegate('click', '.btn-report-reject', (trigger) => handle(trigger, 'reject'));
}

/** 같은 줄의 나머지 처리 버튼도 함께 잠가, 요청이 도는 동안 이중 클릭(예: 삭제+반려 동시 클릭)을 막는다. */
function rowButtons(item) {
    return item ? Array.from(item.querySelectorAll('button')) : [];
}

// 삭제 확인 문구에 대상을 명시한다(문서 5.5, delete-confirm.js와 같은 규칙). 제목·댓글
// 내용이 길면 모달이 한눈에 안 들어오므로 자른다.
function truncate(text, max) {
    if (!text) {
        return '';
    }
    return text.length > max ? `${text.slice(0, max)}…` : text;
}

function targetOf(trigger) {
    const item = trigger.closest('.report-list__item');
    const isPost = item?.dataset.targetKey?.startsWith('POST:') ?? false;
    return { isPost, name: truncate(item?.dataset.targetName, isPost ? 40 : 30) };
}

function setRowDisabled(item, disabled) {
    rowButtons(item).forEach((button) => {
        button.disabled = disabled;
    });
}

async function handle(trigger, action, suspendDays = 0) {
    const item = trigger.closest('.report-list__item');
    const id = item?.dataset.reportId;
    if (!id || trigger.disabled) {
        return;
    }

    setRowDisabled(item, true);
    try {
        const query = suspendDays > 0 ? `?suspendDays=${suspendDays}` : '';
        await api.post(`/api/v1/admin/reports/${id}/${action}${query}`);
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        setRowDisabled(item, false);
    }
}

/**
 * 정지 해제(/admin/users). 신고 목록과 같은 카드·같은 처리 방식이라 여기 함께 둔다.
 */
async function liftSuspension(trigger) {
    const item = trigger.closest('.report-list__item');
    const id = item?.dataset.userId;
    if (!id || trigger.disabled) {
        return;
    }

    setRowDisabled(item, true);
    try {
        await api.post(`/api/v1/admin/users/${id}/suspension/lift`);
        window.location.reload();
    } catch (error) {
        showToast(messageOf(error), 'danger');
        setRowDisabled(item, false);
    }
}
