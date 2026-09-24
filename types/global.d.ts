import * as bootstrapNamespace from 'bootstrap';

/**
 * CDN의 classic script가 올려주는 Bootstrap 전역을 IDE·타입 검사기에 알려준다.
 *
 * ESM으로 import하지 않는 이유는 SRI가 깨지고 모듈 평가 시점에 네트워크 왕복이 생기기
 * 때문이다(templates/layout/footer.html 주석 참고). 그래서 런타임에는 전역이고, 여기서는
 * 그 사실을 선언만 한다 — 이 파일은 번들에 포함되지 않으며 실행되지도 않는다.
 *
 * 위치가 src/main/resources/static 밖인 것도 의도다. 그 아래에 두면 운영 JAR에 함께
 * 포장되어 /js/types/global.d.ts 로 공개 서빙된다(실제로 그랬다).
 */
declare global {
    const bootstrap: typeof bootstrapNamespace;

    interface Window {
        bootstrap: typeof bootstrapNamespace;
        /**
         * mount-failure.js가 올려주는 전역. Vue 아일랜드가 마운트에 실패하면(JSON 파싱 실패,
         * setup 중 예외 등) 마운트 지점 id를 넘겨 불러, 빈 화면 대신 최소 안내로 대체한다
         * (개선 보고서 F12, src/vue/shared/mountIsland.js).
         */
        kraftVueMountFailed?: (mountPointId: string) => void;
    }
}
