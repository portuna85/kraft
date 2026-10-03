// Bootstrap JS 중 이 앱이 쓰는 Modal·Toast만 담아 전역 `bootstrap`으로 노출한다(FE-06).
//
// 예전에는 bootstrap.min.js(60KB, 전 컴포넌트)를 classic script로 통째로 커밋해 모든 페이지에서
// 받았다. 이 앱은 core/bootstrap-ui.js가 감싸는 Modal·Toast만 쓰므로(드롭다운·툴팁·팝오버를
// 쓰지 않아 Popper도 필요 없다), 필요한 컴포넌트만 node_modules에서 번들한다 — 버전은
// package-lock.json이 고정하므로 벤더 파일 복사본과 버전이 어긋날 일도 없다.
//
// 전역 객체를 유지하는 이유: core/bootstrap-ui.js는 plain JS(번들하지 않음)와 Vue 번들 양쪽에서
// 쓰이는데, 모듈로 직접 import하면 Vue 번들에 Bootstrap이 한 벌 더 들어가 Modal 인스턴스가
// 둘로 갈라진다. 한 곳에서만 만들어 전역으로 공유한다. 컴포넌트를 더 쓰게 되면 여기에 추가한다.
import Modal from 'bootstrap/js/dist/modal';
import Toast from 'bootstrap/js/dist/toast';

// 전역 타입(@types/bootstrap)은 패키지 전체를 가정하지만 여기서는 Modal·Toast만 올린다.
// core/bootstrap-ui.js가 쓰기 전에 존재 여부를 확인하므로 안전하다.
window.bootstrap = /** @type {any} */ ({ Modal, Toast });
