/** mount.js가 가져오는 SFC를 JS 타입 검사에서 불투명한 컴포넌트로 취급한다(검사는 vue-tsc가 따로 한다). */
declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<object, object, unknown>;
  export default component;
}
