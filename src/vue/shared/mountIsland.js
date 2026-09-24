// @ts-check
import { createApp } from 'vue';

/**
 * Vue 아일랜드 진입점(각 mount.js)이 공통으로 하는 일을 한데 묶는다(FE-17).
 *
 * 예전에는 절반의 아일랜드(signup·forgot-password·password-reset·recommend)가 마운트 가드
 * 자체가 없었고, JSON을 검증하던 나머지(comments·post-edit·post-save)도 `createApp().mount()`
 * 호출 자체는 try/catch로 감싸지 않았다 — 설정(setup) 중 예외가 나면 안내 없이 빈 화면만
 * 남았다. 이 헬퍼는 세 가지를 항상 함께 한다: (1) 페이지 데이터 파싱·검증(있다면),
 * (2) `app.config.errorHandler` 등록, (3) 마운트 자체를 try/catch로 감싸기. 무엇이 실패하든
 * `window.kraftVueMountFailed`(mount-failure.js)로 같은 안내를 띄운다.
 *
 * @param {Object} options
 * @param {HTMLElement | null} options.mountPoint 마운트 지점. null이면(그 페이지에 이
 *   아일랜드가 없음) 조용히 아무 일도 하지 않는다 — 기존 각 mount.js의 규칙과 같다.
 * @param {import('vue').Component} options.component
 * @param {Record<string, any> | (() => (Record<string, any> | null))} [options.props] 정적
 *   props 객체를 그대로 주거나, 페이지 데이터를 파싱·검증해 props를 만드는 함수를 준다.
 *   함수가 null을 반환하면(JSON이 없거나·깨졌거나·모양이 다름) 마운트를 포기하고
 *   kraftVueMountFailed를 부른다 — 각 컴포넌트가 필수로 참조하는 값이 없을 때 바로
 *   죽는 것보다 안전하다.
 */
export function mountIsland({ mountPoint, component, props }) {
    if (!mountPoint) {
        return;
    }

    /** @type {Record<string, any> | null | undefined} */
    let resolvedProps;
    if (typeof props === 'function') {
        resolvedProps = props();
        if (resolvedProps == null) {
            window.kraftVueMountFailed?.(mountPoint.id);
            return;
        }
    } else {
        resolvedProps = props;
    }

    try {
        const app = createApp(component, resolvedProps ?? {});
        app.config.errorHandler = (err, instance, info) => {
            console.error(`[kraft] Vue 아일랜드(${mountPoint.id}) 실행 중 오류가 발생했습니다. info=${info}`, err);
        };
        app.mount(mountPoint);
    } catch (err) {
        console.error(`[kraft] Vue 아일랜드(${mountPoint.id})를 마운트하지 못했습니다.`, err);
        window.kraftVueMountFailed?.(mountPoint.id);
    }
}

/**
 * `<script type="application/json">` 태그의 내용을 파싱하고, validate가 참을 돌려줄 때만
 * 그 값을 쓴다. `mountIsland`의 `props` 함수 안에서 쓰기 위한 작은 헬퍼다.
 *
 * @param {string} dataElementId
 * @param {(parsed: any) => boolean} validate
 * @returns {any | null}
 */
export function parsePageData(dataElementId, validate) {
    try {
        const parsed = JSON.parse(document.getElementById(dataElementId)?.textContent || 'null');
        return validate(parsed) ? parsed : null;
    } catch {
        return null;
    }
}
