import * as flash from './ui/flash.js';
import * as siteNav from './features/site-nav.js';
import * as account from './features/account.js';
import * as signup from './features/signup.js';
import * as postForm from './features/post-form.js';
import * as deleteConfirm from './features/delete-confirm.js';

/**
 * 진입점.
 *
 * `<script type="module">`은 기본이 defer라 문서 파싱이 끝난 뒤에 실행된다. 그래서 예전의
 * `$(function () { ... })` 같은 준비 대기가 필요 없다.
 *
 * 이 파일은 layout/footer를 통해 **모든 페이지**에 실린다. 따라서 각 기능의 init()은 자기
 * 화면이 아닐 때도 호출된다 — 모든 init()이 필요한 요소가 없으면 조용히 돌아가야 하고,
 * 하나라도 던지면 그 뒤에 등록될 기능이 전부 죽는다. core/dom.js의 헬퍼들이 그 규칙을 지킨다.
 */
flash.consume();

siteNav.init();
account.init();
signup.init();
postForm.init();
deleteConfirm.init();
