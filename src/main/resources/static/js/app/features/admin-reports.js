import { delegate, qs } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { showToast } from '../ui/toast.js';

/**
 * 관리자 화면의 처리 버튼들 — 신고 목록의 삭제·반려와 정지 회원 목록의 해제.
 *
 * 처리에 성공하면 현재 페이지를 다시 불러온다. 로컬에서 줄만 지우면 "처리 대기" 카운트·
 * 페이지 수가 서버 상태와 어긋날 수 있다(F05) — post-edit/post-save가 성공 후
 * window.location.href로 전체 이동하는 것과 같은 관례다.
 */
export function init() {
    if (!qs('.report-list')) {
        return;
    }

    delegate('click', '.btn-lift-suspension', liftSuspension);
    delegate('click', '.btn-report-resolve', (trigger) => handle(trigger, 'resolve'));
    delegate('click', '.btn-report-suspend', (trigger) => handle(trigger, 'resolve', Number(trigger.dataset.suspendDays)));
    delegate('click', '.btn-report-reject', (trigger) => handle(trigger, 'reject'));
}

/** 같은 줄의 나머지 처리 버튼도 함께 잠가, 요청이 도는 동안 이중 클릭(예: 삭제+반려 동시 클릭)을 막는다. */
function rowButtons(item) {
    return item ? Array.from(item.querySelectorAll('button')) : [];
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
