// @ts-check

/**
 * 텍스트를 클립보드에 복사한다. 반드시 사용자 클릭 핸들러 안에서 부른다 — 클립보드 쓰기는 사용자 제스처와
 * 보안 컨텍스트(https·localhost)가 필요하다.
 *
 * `navigator.clipboard.writeText`를 먼저 쓰고, 없거나 거절되면(권한·옛 브라우저·비보안 컨텍스트) 숨긴
 * textarea + `execCommand('copy')`로 물러선다. 두 번째 경로는 폐기 예정 API지만 지원 범위(iOS 15+)의 일부
 * 환경에서는 유일한 수단이다. 클립보드와 문서를 주입할 수 있게 해 브라우저 없이 검사한다.
 *
 * @param {string} text
 * @param {{ clipboard?: { writeText?: (value: string) => Promise<void> } | null, doc?: Document | null }} [env]
 * @returns {Promise<boolean>} 복사했으면 true
 */
export async function copyText(text, env = {}) {
    const clipboard = 'clipboard' in env ? env.clipboard : globalThis.navigator?.clipboard;
    const doc = 'doc' in env ? env.doc : globalThis.document;

    if (clipboard?.writeText) {
        try {
            await clipboard.writeText(text);
            return true;
        } catch {
            // 권한이 거절됐거나 문서에 포커스가 없다 — 아래 폴백으로 넘어간다.
        }
    }
    return legacyCopy(text, doc);
}

/**
 * @param {string} text
 * @param {Document | null | undefined} doc
 * @returns {boolean}
 */
function legacyCopy(text, doc) {
    if (!doc?.body || typeof doc.execCommand !== 'function') {
        return false;
    }
    const textarea = doc.createElement('textarea');
    textarea.value = text;
    // 화면에 보이거나 모바일 키보드가 뜨지 않게 한다. 인라인 style 속성이 아니라 CSSOM으로 지정한다(CSP style-src 'self').
    textarea.setAttribute('readonly', '');
    textarea.setAttribute('aria-hidden', 'true');
    textarea.style.position = 'fixed';
    textarea.style.top = '0';
    textarea.style.left = '0';
    textarea.style.opacity = '0';
    doc.body.appendChild(textarea);
    try {
        textarea.select();
        return doc.execCommand('copy');
    } catch {
        return false;
    } finally {
        doc.body.removeChild(textarea);
    }
}
