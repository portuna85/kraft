// KF-10(docs/improvement.md): 관리자 CSP가 인라인 스크립트를 막아 onsubmit="return
// confirm(...)"이 무시되던 문제를 고친다. 같은 출처 외부 스크립트는 CSP가 원래부터
// 허용하므로, data-confirm 속성을 읽어 폼 submit을 가로채는 위임 리스너로 대신한다.
document.addEventListener("submit", function (event) {
  var form = event.target;
  if (!(form instanceof HTMLFormElement)) return;

  var message = form.getAttribute("data-confirm");
  if (!message) return;

  if (!window.confirm(message)) {
    event.preventDefault();
  }
});
