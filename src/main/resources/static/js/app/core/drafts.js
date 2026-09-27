/**
 * 글쓰기·수정 화면(src/vue/post-save, src/vue/post-edit)의 자동 임시 저장이 쓰는 키는
 * 전부 `kraft:draft:` 접두사를 쓴다(useDraftAutosave.js·draftStorage.js 참고. 회원 id가
 * 들어간 형태: `kraft:draft:{userId}:post-save`, `kraft:draft:{userId}:post-edit:{postId}`).
 *
 * 로그아웃·비밀번호 변경·회원 탈퇴로 세션이 끝나면 이 계정의 초안을 지운다(전체 리뷰
 * 2026-09-26 A-FE-03) — 공용 PC에서 다음 사용자가 로그인해도 초안이 남지 않게 하는 주
 * 방어다(서버 세션 만료로 인한 로그아웃은 이 코드가 실행될 기회 자체가 없어 잡을 수 없다).
 *
 * localStorage 접근이 막힌 환경(프라이빗 모드 등)에서도 로그아웃 등의 흐름 자체는 정상
 * 동작해야 하므로 모든 실패를 조용히 삼킨다(draftStorage.js와 같은 관례).
 */
const DRAFT_KEY_PREFIX = 'kraft:draft:';

export function clearAllDrafts() {
    try {
        const keysToRemove = Object.keys(window.localStorage)
            .filter((key) => key.startsWith(DRAFT_KEY_PREFIX));
        keysToRemove.forEach((key) => window.localStorage.removeItem(key));
    } catch {
        // 저장소를 쓸 수 없거나 접근 자체가 막혀도 호출자의 흐름(로그아웃 등)은 계속된다.
    }
}
