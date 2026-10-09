import * as bootstrapNamespace from 'bootstrap';

/**
 * src/vue/bootstrap/entry.js가 올려주는 Bootstrap 전역(layout/footer.html 참고)을 IDE·타입 검사기에 알려준다. plain JS는 직접 import하지 않고 전역으로 쓰므로 여기서는 선언만 한다 —
 * 이 파일은 번들에 포함되지 않으며 실행되지도 않는다.
 *
 * 위치가 src/main/resources/static 밖인 것도 의도다. 그 아래에 두면 운영 JAR에 함께 포장되어 /js/types/global.d.ts 로 공개 서빙된다.
 */
declare global {
    const bootstrap: typeof bootstrapNamespace;

    interface Window {
        bootstrap: typeof bootstrapNamespace;
        /** mount-failure.js가 올려주는 전역. Vue 아일랜드가 마운트에 실패하면(JSON 파싱 실패, setup 중 예외 등) 마운트 지점 id를 넘겨 불러, 빈 화면 대신 최소 안내로 대체한다. */
        kraftVueMountFailed?: (mountPointId: string) => void;
        /** theme-init.js(head, 동기)가 올려주는 전역. 테마 키·순서와 적용 함수를 노출한다. */
        kraftTheme?: {
            STORAGE_KEY: string;
            ORDER: string[];
            apply: (mode: string) => void;
        };
    }
}
