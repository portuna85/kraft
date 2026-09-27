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
 * `window.localStorage`에 안전하게 접근한다. `getItem`/`setItem` 호출 실패는 아래
 * 함수들이 각자 try로 감싸지만, **속성 접근 자체**(`window.localStorage`)가 SecurityError로
 * 던지는 환경(서드파티 컨텍스트에서 저장소가 차단된 경우 등)이 있다 — 그 접근이 컴포넌트
 * setup() 도중(useDraftAutosave의 기본 인자 평가) 그대로 터지면 캐치할 try가 없어 Vue
 * 마운트 전체가 실패하고 화면이 빈 채로 남는다(전체 리뷰 2026-09-26 FE-01/02). 이 함수는
 * 그 접근 자체를 감싸 실패 시 null을 돌려주고, 호출자는 null이면 임시 저장을 조용히
 * 건너뛴다.
 *
 * @returns {Storage | null}
 */
export function safeLocalStorage() {
    try {
        return window.localStorage;
    } catch {
        return null;
    }
}

/**
 * 저장된 초안을 읽는다. 없거나, 형식이 깨졌거나, ttlMs보다 오래됐으면 null을 반환하고
 * (오래된 경우) 저장소에서 지운다. `storage`가 null이면(safeLocalStorage 참고) 저장소가
 * 아예 없는 것으로 보고 조용히 null을 돌려준다.
 *
 * @param {Storage | null} storage
 * @param {string} key
 * @param {number} ttlMs
 * @returns {Record<string, unknown> | null}
 */
export function readDraft(storage, key, ttlMs) {
    let raw;
    try {
        raw = storage?.getItem(key);
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
 * 초안을 저장한다. data에 savedAt(현재 시각)을 더해 그대로 직렬화한다. `storage`가 null이면
 * 아무것도 하지 않는다.
 *
 * @param {Storage | null} storage
 * @param {string} key
 * @param {Record<string, unknown>} data
 */
export function writeDraft(storage, key, data) {
    try {
        storage?.setItem(key, JSON.stringify({ savedAt: Date.now(), ...data }));
    } catch {
        // 저장 실패는 이번 작성 세션에서만 임시 저장이 안 되는 정도로 넘어간다.
    }
}

/**
 * @param {Storage | null} storage
 * @param {string} key
 */
export function clearDraft(storage, key) {
    try {
        storage?.removeItem(key);
    } catch {
        // 지우기 실패는 다음 저장이 덮어쓸 때까지 남아있는 정도로 넘어간다.
    }
}
