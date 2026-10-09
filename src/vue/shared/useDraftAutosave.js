import { getCurrentInstance, onBeforeUnmount, ref } from 'vue';
import { clearDraft, readDraft, safeLocalStorage, writeDraft } from './draftStorage.js';

const DEFAULT_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7일
const DEBOUNCE_MS = 800;

/**
 * 글쓰기·수정 화면의 자동 임시 저장. useUnsavedGuard(이탈 경고)와 나란히 쓴다 — 그쪽은 "잃을 수 있다고 경고", 이쪽은 "잃어도 되찾을 수 있게" 한다.
 * 사진은 File/blob URL이라 직렬화할 수 없으므로 다루지 않는다 — 호출자는 직렬화 가능한 필드(title·content·category)만 담는다.
 *
 * @param {string} storageKey
 * @param {import('vue').Reactive<Record<string, unknown>>} draft
 * @param {{ ttlMs?: number, storage?: Storage | null }} [options]
 */
export function useDraftAutosave(storageKey, draft, options = {}) {
    const ttlMs = options.ttlMs ?? DEFAULT_TTL_MS;
    // window.localStorage 속성 접근 자체가 던질 수 있어(safeLocalStorage 참고) 직접 읽지 않는다 — 이 기본값은 setup() 도중 평가되므로 여기서 던지면 try로 감쌀 곳이 없어 Vue 마운트 전체가 실패한다.
    const storage = options.storage ?? safeLocalStorage();

    const available = ref(false);
    let pendingRestore = /** @type {Record<string, unknown> | null} */ (null);
    let timer = /** @type {ReturnType<typeof setTimeout> | null} */ (null);

    /**
     * 저장된 초안이 있고 지금 보여줄 만한 값이면(isUseless가 false) 배너를 띄운다. 마운트 시(또는 PostEditApp의 편집 진입 시) 한 번만 호출한다.
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
     * draft가 바뀔 때마다 호출한다(watch(draft, () => autosave.schedule(), { deep: true })). 사용자가 입력했다는 뜻이므로 배너가 떠 있었다면 치운다(무시하고 새로 쓰기 시작한 것으로 본다).
     */
    function schedule() {
        available.value = false;
        pendingRestore = null;
        if (timer) {
            clearTimeout(timer);
        }
        timer = setTimeout(flush, DEBOUNCE_MS);
    }

    /** 대기 중인 저장이 있으면 지금 쓴다. 페이지를 떠나기 직전 마지막 800ms 입력을 잃지 않게 한다. */
    function flush() {
        if (!timer) {
            return;
        }
        clearTimeout(timer);
        timer = null;
        writeDraft(storage, storageKey, { ...draft });
    }

    // 컴포넌트 밖(단위 테스트)에서 호출될 수 있어 인스턴스가 있을 때만 생명주기에 연결한다.
    if (getCurrentInstance() && typeof window !== 'undefined') {
        window.addEventListener('pagehide', flush);
        onBeforeUnmount(() => {
            window.removeEventListener('pagehide', flush);
            flush();
        });
    }

    return { available, checkAvailable, restore, discard, schedule, flush };
}
