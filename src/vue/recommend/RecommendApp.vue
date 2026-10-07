<script setup>
import { nextTick, ref, watch } from 'vue';
import { ballColorClass } from './ballColor.js';
import { useRecommendation } from './useRecommendation.js';

/**
 * 번호 추천 화면.
 *
 * 진입 시 자동 생성은 하지 않는다 — 사용자가 "번호 추천 생성" 버튼을 눌렀을 때만 서버에
 * 요청한다. 선택 옵션은 없다(조건은 useRecommendation이 고정). 이 컴포넌트는 화면 표시와
 * 포커스 이동만 맡는다.
 */
const {
    status,
    result,
    errorMessage,
    liveAnnouncement,
    canSubmit,
    generate,
} = useRecommendation();

const resultHeading = ref(/** @type {HTMLElement | null} */ (null));

// 성공 응답이 오면 결과 제목으로 포커스를 옮긴다 — 스크린리더 사용자가 생성 버튼 다음에
// 이어질 결과를 놓치지 않게 한다.
watch(status, async (next) => {
    if (next === 'ready') {
        await nextTick();
        resultHeading.value?.focus();
    }
});
</script>

<template>
  <div class="recommend">
    <form
      class="recommend__form"
      @submit.prevent="generate"
    >
      <button
        id="btn-recommend-generate"
        type="submit"
        class="btn btn-primary"
        :disabled="!canSubmit"
      >
        {{ status === 'generating' ? '생성 중…' : '번호 추천 생성' }}
      </button>
    </form>

    <p
      v-if="status === 'generating'"
      class="form-progress"
      aria-hidden="true"
    >
      추천 번호를 생성하는 중입니다.
    </p>

    <p
      class="visually-hidden"
      aria-live="polite"
    >
      {{ liveAnnouncement }}
    </p>

    <div
      v-if="status === 'history-not-ready'"
      class="recommend__notice"
      role="status"
    >
      추천 이력이 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요.
    </div>

    <div
      v-else-if="status === 'error'"
      class="recommend__error"
      role="alert"
    >
      {{ errorMessage }}
    </div>

    <section
      v-if="result"
      class="recommend__results"
    >
      <h2
        ref="resultHeading"
        tabindex="-1"
      >
        추천 결과
      </h2>

      <p class="recommend__history-basis">
        과거 {{ result.historyThroughRound }}회차까지 검증된 조합을 제외합니다.
      </p>

      <ol class="recommend__items">
        <li
          v-for="item in result.items"
          :key="item.position"
          class="recommend__item"
        >
          <span class="visually-hidden">추천 {{ item.position }}: {{ item.numbers.join(', ') }}</span>
          <ul
            class="lotto-balls"
            aria-hidden="true"
          >
            <li
              v-for="n in item.numbers"
              :key="n"
              class="lotto-ball"
              :class="ballColorClass(n)"
            >
              {{ n }}
            </li>
          </ul>
        </li>
      </ol>
    </section>
  </div>
</template>
