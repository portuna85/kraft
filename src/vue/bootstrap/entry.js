// Bootstrap JS 중 이 앱이 쓰는 Modal·Toast만 담아 전역 `bootstrap`으로 노출한다. 드롭다운·툴팁·팝오버를 쓰지 않아 Popper도 필요 없고, 버전은 package-lock.json이 고정한다.
//
// 전역 객체를 유지하는 이유: core/bootstrap-ui.js가 plain JS와 Vue 번들 양쪽에서 쓰이는데, 모듈로 직접 import하면 번들마다 Bootstrap이 한 벌 더 들어가 Modal 인스턴스가 갈라질 수 있다.
// 한 곳에서만 만들어 전역으로 공유한다. 컴포넌트를 더 쓰게 되면 여기에 추가한다.
import Modal from 'bootstrap/js/dist/modal';
import Toast from 'bootstrap/js/dist/toast';

// 전역 타입(@types/bootstrap)은 패키지 전체를 가정하지만 여기서는 Modal·Toast만 올린다.
// core/bootstrap-ui.js가 쓰기 전에 존재 여부를 확인하므로 안전하다.
window.bootstrap = /** @type {any} */ ({ Modal, Toast });
