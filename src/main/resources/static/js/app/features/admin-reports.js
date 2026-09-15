import { delegate, qs } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { showToast } from '../ui/toast.js';

/**
 * 관리자 신고 목록의 처리 버튼(삭제·반려).
 *
 * 처리한 줄만 목록에서 지우고 화면은 그대로 둔다 — 새로고침하면 페이지 위치와 스크롤을 잃어
 * 여러 건을 잇따라 볼 때 번거롭다. 같은 대상의 다른 신고도 서버가 함께 정리하므로, 그 줄들도
 * 여기서 지운다.
 */
export function init() {
    if (!qs('.report-list')) {
        return;
    }

    delegate('click', '.btn-report-resolve', (trigger) => handle(trigger, 'resolve'));
    delegate('click', '.btn-report-reject', (trigger) => handle(trigger, 'reject'));
}

async function handle(trigger, action) {
    const item = trigger.closest('.report-list__item');
    const id = item?.dataset.reportId;
    if (!id || trigger.disabled) {
        return;
    }

    trigger.disabled = true;
    try {
        await api.post(`/api/v1/admin/reports/${id}/${action}`);
        showToast(action === 'resolve' ? '대상을 삭제하고 신고를 처리했습니다.' : '신고를 반려했습니다.', 'success');
        removeHandled(item, action);
    } catch (error) {
        showToast(messageOf(error), 'danger');
        trigger.disabled = false;
    }
}

function removeHandled(item, action) {
    const { targetKey } = item.dataset;
    item.remove();

    if (action === 'resolve' && targetKey) {
        // 서버가 같은 대상("POST:12" 꼴)의 대기 신고를 함께 처리했다. 화면에서도 같이 지운다.
        document.querySelectorAll('.report-list__item').forEach((other) => {
            if (other.dataset.targetKey === targetKey) {
                other.remove();
            }
        });
    }

    if (!document.querySelector('.report-list__item')) {
        // 남은 줄이 없으면 빈 상태를 보여준다. 처리할 것이 없다는 사실도 정보다.
        const list = qs('.report-list');
        if (list) {
            list.outerHTML = '<div class="empty-state"><p class="empty-state__text">처리할 신고가 없습니다.</p></div>';
        }
    }
}
