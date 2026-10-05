/**
 * localStorage를 안전하게 쓰는 공용 래퍼(FE-30). 프라이빗 모드·서드파티 컨텍스트처럼 저장소가 막힌 환경에서는
 * `window.localStorage`를 읽는 것(속성 접근)부터 SecurityError로 던질 수 있다. 화면은 저장소 없이도 기본값으로
 * 계속 동작해야 하므로 모든 실패를 삼키고 호출자에게 null·false로만 알린다. 테마·글자 크기·초안·"더 보기" 상태가
 * 각자 try/catch를 따로 쓰던 것을 모았다.
 */

/**
 * `window.localStorage`. 접근 자체가 던지거나 막혀 있으면 null이다.
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
 * @param {string} key
 * @returns {string | null} 없거나 읽을 수 없으면 null
 */
export function readItem(key) {
    try {
        return safeLocalStorage()?.getItem(key) ?? null;
    } catch {
        return null;
    }
}

/**
 * @param {string} key
 * @param {string} value
 * @returns {boolean} 저장했으면 true
 */
export function writeItem(key, value) {
    try {
        const storage = safeLocalStorage();
        if (!storage) {
            return false;
        }
        storage.setItem(key, value);
        return true;
    } catch {
        return false;
    }
}

/**
 * @param {string} key
 */
export function removeItem(key) {
    try {
        safeLocalStorage()?.removeItem(key);
    } catch {
        // 지우지 못해도 다음 읽기에서 형식·만료 검사가 걸러 낸다.
    }
}
