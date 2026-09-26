/**
 * 글쓰기·수정 화면의 자동 임시 저장이 쓰는 순수 저장소 함수. `storage`(보통
 * `window.localStorage`)는 호출자가 주입한다 — 테스트에서는 메모리 목으로 대체하고,
 * 실제 브라우저 저장소 접근에 필요한 try/catch는 전부 이 모듈이 감싼다.
 *
 * PostEditApp.vue의 글자크기 저장(kraft:post-font-scale)과 같은 관례: 저장소를 쓸 수 없는
 * 환경(프라이빗 모드 등)에서도 화면은 기본값으로 계속 동작해야 하므로 모든 실패를 조용히
 * 삼킨다.
 */

/**
 * 저장된 초안을 읽는다. 없거나, 형식이 깨졌거나, ttlMs보다 오래됐으면 null을 반환하고
 * (오래된 경우) 저장소에서 지운다.
 *
 * @param {Storage} storage
 * @param {string} key
 * @param {number} ttlMs
 * @returns {Record<string, unknown> | null}
 */
export function readDraft(storage, key, ttlMs) {
    let raw;
    try {
        raw = storage.getItem(key);
    } catch {
        return null;
    }
    if (!raw) {
        return null;
    }

    let parsed;
    try {
        parsed = JSON.parse(raw);
    } catch {
        return null;
    }
    if (!parsed || typeof parsed !== 'object' || typeof parsed.savedAt !== 'number') {
        return null;
    }

    if (Date.now() - parsed.savedAt > ttlMs) {
        clearDraft(storage, key);
        return null;
    }

    const data = { ...parsed };
    delete data.savedAt;
    return data;
}

/**
 * 초안을 저장한다. data에 savedAt(현재 시각)을 더해 그대로 직렬화한다.
 *
 * @param {Storage} storage
 * @param {string} key
 * @param {Record<string, unknown>} data
 */
export function writeDraft(storage, key, data) {
    try {
        storage.setItem(key, JSON.stringify({ savedAt: Date.now(), ...data }));
    } catch {
        // 저장 실패는 이번 작성 세션에서만 임시 저장이 안 되는 정도로 넘어간다.
    }
}

/**
 * @param {Storage} storage
 * @param {string} key
 */
export function clearDraft(storage, key) {
    try {
        storage.removeItem(key);
    } catch {
        // 지우기 실패는 다음 저장이 덮어쓸 때까지 남아있는 정도로 넘어간다.
    }
}
