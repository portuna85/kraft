import { ref } from 'vue';
import { clearDraft, readDraft, writeDraft } from './draftStorage.js';

const DEFAULT_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7일
const DEBOUNCE_MS = 800;

/**
 * 글쓰기·수정 화면의 자동 임시 저장. useUnsavedGuard(이탈 경고)를 대체하지 않고 나란히
 * 쓴다 — 그쪽은 "잃을 수 있다고 경고", 이쪽은 "잃어도 되찾을 수 있게" 한다.
 *
 * 사진은 File/blob URL이라 직렬화할 수 없으므로 다루지 않는다 — 호출자는 title·content·
 * category 같은 직렬화 가능한 필드만 담는다.
 *
 * @param {string} storageKey
 * @param {import('vue').Reactive<Record<string, unknown>>} draft
 * @param {{ ttlMs?: number, storage?: Storage }} [options]
 */
export function useDraftAutosave(storageKey, draft, options = {}) {
    const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
    const storage = options.storage ?? window.localStorage;

    const available = ref(false);
    let pendingRestore = /** @type {Record<string, unknown> | null} */ (null);
    let timer = /** @type {ReturnType<typeof setTimeout> | null} */ (null);

    /**
     * 저장된 초안이 있고, 지금 보여줄 만한 값이면(isUseless가 false) 배너를 띄운다.
     * 마운트 시(또는 PostEditApp의 편집 진입 시) 한 번만 호출한다.
     *
     * @param {(stored: Record<string, unknown>) => boolean} isUseless
     */
    function checkAvailable(isUseless) {
        const stored = readDraft(storage, storageKey, ttlMs);
        if (stored && !isUseless(stored)) {
            pendingRestore = stored;
            available.value = true;
        }
    }

    /** 배너의 "복원"에서 호출한다. apply가 draft 필드를 채운다. */
    function restore(apply) {
        if (pendingRestore) {
            apply(pendingRestore);
        }
        available.value = false;
        pendingRestore = null;
    }

    /** 배너의 "새로 시작", 제출 성공, 편집 취소에서 호출한다. */
    function discard() {
        if (timer) {
            clearTimeout(timer);
            timer = null;
        }
        available.value = false;
        pendingRestore = null;
        clearDraft(storage, storageKey);
    }

    /**
     * draft가 바뀔 때마다 호출한다(watch(draft, () => autosave.schedule(), { deep: true })).
     * 사용자가 뭔가 입력했다는 뜻이므로 배너가 떠 있었다면 치운다 — 무시하고 새로 쓰기
     * 시작한 것으로 본다.
     */
    function schedule() {
        available.value = false;
        pendingRestore = null;
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(() => {
            timer = null;
            writeDraft(storage, storageKey, { ...draft });
        }, DEBOUNCE_MS);
    }

    return { available, checkAvailable, restore, discard, schedule };
}
