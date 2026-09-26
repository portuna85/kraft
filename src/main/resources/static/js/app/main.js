import * as flash from './ui/flash.js';
import * as siteNav from './features/site-nav.js';

/**
 * 진입점.
 *
 * `<script type="module">`은 기본이 defer라 문서 파싱이 끝난 뒤에 실행된다. 그래서 예전의
 * `$(function () { ... })` 같은 준비 대기가 필요 없다.
 *
 * 이 파일은 layout/footer를 통해 **모든 페이지**에 실린다. 헤더(siteNav)는 모든 화면에
 * 있으므로 정적으로 불러오지만, 나머지 기능은 대상 DOM이 있는 페이지에서만 동적으로
 * 불러온다 — 예전에는 6개 기능(계정 모달·삭제 확인·신고·관리자 처리)을 무조건 전부
 * import해서, 예를 들어 /login 화면이 실제로 쓰는 것의 3배가 넘는 코드를 받고 있었다.
 *
 * 대상 DOM 판정은 각 화면이 서버에서 렌더링한 요소 또는 같은 페이지의 Vue 아일랜드가
 * 마운트하며 그린 요소를 본다. Vue 아일랜드는 이 스크립트보다 문서 앞쪽의
 * `<script type="module">`이라 모듈 그래프 실행 순서상 항상 먼저 실행되고 마운트를
 * 마친다(module script는 defer 의미이면서 문서 순서를 지킨다) — 그래서 여기서 querySelector로
 * 확인하는 시점에는 이미 렌더링이 끝나 있다.
 */
flash.consume();
siteNav.init();

/**
 * `selector`에 맞는 요소가 있을 때만 `modulePath`를 불러와 `init()`을 부른다.
 *
 * 실패하면 콘솔에 남긴다 — 조용히 삼키면 "버튼은 보이는데 눌러도 반응이 없다"는 원인 불명
 * 버그가 된다. E2E 픽스처가 콘솔 오류를 실패로 잡으므로(fixtures.js) 회귀가 여기서 드러난다.
 */
async function loadIf(selector, modulePath) {
    if (!document.querySelector(selector)) {
        return;
    }
    try {
        const feature = await import(modulePath);
        feature.init();
    } catch (error) {
        console.error(`기능을 불러오지 못했습니다: ${modulePath}`, error);
    }
}

// 계정 모달(로그아웃·비밀번호 변경·탈퇴·인증 메일 재발송)은 헤더에서 시작하고 로그인
// 상태에 따라 sec:authorize가 걸러낸 것만 렌더링된다.
loadIf('#btn-logout, #changePasswordModal, #withdrawModal, #resendVerificationModal', './features/account.js');

// 게시글·댓글 공용 삭제 확인 모달. 트리거 버튼은 Vue 아일랜드(post-edit·comments)가 그린다.
// 댓글 목록은 위임 클릭 핸들러라 나중에 추가되는 항목도 그대로 잡지만, 로드 자체는
// 최초 DOM 스냅숏으로 한 번만 판단한다. 남의 글이라 '[data-target-kind="post"]'가 없고
// 기존 댓글도 없으면 이 셀렉터만으로는 아무것도 찾지 못해 모듈이 로드되지 않았고, 그 상태에서
// 방금 쓴 첫 댓글의 삭제 버튼은 위임 핸들러가 없어 눌러도 반응이 없었다(개선 보고서
// "저장 중 댓글 변경과 동적 삭제 모듈 누락"). '#comments-heading'은 댓글이 0개여도, 남의
// 글이어도 댓글 영역이 있는 페이지라면 항상 최초 DOM에 존재하므로 이 경우를 메운다.
// '#post-app, #comments-app'은 그 Vue 아일랜드가 마운트하는 자리 자체라, Vue 청크 로드가
// 늦어져 트리거 버튼이 아직 그려지기 전이어도(개선 보고서 "동적 DOM과 기능 초기화 시점")
// post-update.html이 서버에서 항상 먼저 렌더링하므로 이 셀렉터만은 확실히 존재한다(F06).
loadIf('[data-target-kind], #comments-heading, #post-app, #comments-app', './features/delete-confirm.js');

// 게시글·댓글 공용 신고 모달. 트리거 버튼도 마찬가지로 Vue 아일랜드가 그리므로 같은 이유로
// 안정된 마운트 지점도 함께 본다(F06).
loadIf('[data-report-kind], #post-app, #comments-app', './features/report-dialog.js');

// 관리자 신고·정지 회원 처리 버튼. 목록이 비어 있으면 .report-list 자체가 렌더링되지 않는다.
loadIf('.report-list', './features/admin-reports.js');

// 목록 "더 보기"(10단계). 마지막 페이지거나 글이 없으면 index.html이 버튼 자체를 렌더링하지
// 않는다.
loadIf('#btn-load-more', './features/load-more.js');

// 다크 모드 토글(11단계). 모든 페이지의 헤더에 항상 있다.
loadIf('#btn-theme-toggle', './features/theme-toggle.js');
