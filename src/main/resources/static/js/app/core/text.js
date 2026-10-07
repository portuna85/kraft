/**
 * 확인 문구에 넣을 대상 이름을 자른다(FE-29). 게시글 제목은 최대 255자, 댓글 내용은 최대 1000자라
 * 그대로 넣으면 모달이 한눈에 안 들어온다. 삭제 확인(delete-confirm)이 쓴다.
 *
 * @param {string | null | undefined} text
 * @param {number} max
 * @returns {string}
 */
export function truncate(text, max) {
    if (!text) {
        return '';
    }
    return text.length > max ? `${text.slice(0, max)}…` : text;
}
