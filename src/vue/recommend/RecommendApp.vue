<script setup>
import { nextTick, ref, watch } from 'vue';
import { STRATEGIES, explanationLabel, useRecommendation } from './useRecommendation.js';

/**
 * 번호 추천 화면.
 *
 * 진입 시 자동 생성은 하지 않는다 — 사용자가 "번호 추천 생성" 버튼을 눌렀을 때만 서버에
 * 요청한다. 상태·검증·응답 순서 관리는 useRecommendation이 담당하고, 이 컴포넌트는 화면
 * 표시와 포커스 이동만 맡는다.
 */
const {
    strategy,
    count,
    selectionMode,
    status,
    result,
    errorMessage,
    liveAnnouncement,
    lockedList,
    excludedList,
    clientValidationError,
    canSubmit,
    isStale,
    toggleNumber,
    stateOf,
    generate,
} = useRecommendation();

const numbers = Array.from({ length: 45 }, (_, i) => i + 1);
const resultHeading = ref(/** @type {HTMLElement | null} */ (null));

// 성공 응답이 오면 결과 제목으로 포커스를 옮긴다 — 스크린리더 사용자가 생성 버튼 다음에
// 이어질 결과를 놓치지 않게 한다.
watch(status, async (next) => {
    if (next === 'ready') {
        await nextTick();
        resultHeading.value?.focus();
    }
});

function numberClass(n) {
    const state = stateOf(n);
    return {
        'recommend-number': true,
        'recommend-number--locked': state === 'locked',
        'recommend-number--excluded': state === 'excluded',
    };
}

function numberLabel(n) {
    const state = stateOf(n);
    if (state === 'locked') return `${n}, 고정됨`;
    if (state === 'excluded') return `${n}, 제외됨`;
    return `${n}, 미선택`;
}
</script>

<template>
  <div class="recommend">
    <p class="recommend__disclaimer">
      추천은 번호를 고르는 도구이며 당첨 확률을 높이는 기능이 아닙니다.
    </p>

    <form
      class="recommend__form"
      @submit.prevent="generate"
    >
      <fieldset class="recommend__field">
        <legend>전략</legend>
        <div
          v-for="option in STRATEGIES"
          :key="option.value"
          class="recommend__strategy"
        >
          <label>
            <input
              v-model="strategy"
              type="radio"
              name="strategy"
              :value="option.value"
            >
            <span class="recommend__strategy-label">{{ option.label }}</span>
          </label>
          <p class="recommend__strategy-desc">
            {{ option.description }}
          </p>
        </div>
      </fieldset>

      <div class="recommend__field">
        <label for="recommend-count">생성 개수</label>
        <input
          id="recommend-count"
          v-model.number="count"
          type="number"
          class="form-control recommend__count"
          min="1"
          max="10"
          step="1"
        >
      </div>

      <fieldset class="recommend__field">
        <legend>번호 선택</legend>
        <div
          class="recommend__mode-toggle"
          role="group"
          aria-label="번호 선택 모드"
        >
          <button
            type="button"
            class="btn btn-sm"
            :class="selectionMode === 'locked' ? 'btn-primary' : 'btn-outline-secondary'"
            :aria-pressed="selectionMode === 'locked'"
            @click="selectionMode = 'locked'"
          >
            고정 {{ lockedList.length }}/5
          </button>
          <button
            type="button"
            class="btn btn-sm"
            :class="selectionMode === 'excluded' ? 'btn-primary' : 'btn-outline-secondary'"
            :aria-pressed="selectionMode === 'excluded'"
            @click="selectionMode = 'excluded'"
          >
            제외 {{ excludedList.length }}개
          </button>
        </div>

        <div
          class="recommend__grid"
          role="group"
          aria-label="1부터 45까지 번호"
        >
          <button
            v-for="n in numbers"
            :key="n"
            type="button"
            :class="numberClass(n)"
            :aria-pressed="stateOf(n) !== 'unselected'"
            :aria-label="numberLabel(n)"
            @click="toggleNumber(n)"
          >
            {{ n }}
          </button>
        </div>

        <p
          v-if="clientValidationError"
          class="recommend__validation"
        >
          {{ clientValidationError }}
        </p>
      </fieldset>

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

      <p
        v-if="isStale"
        class="recommend__stale"
        role="status"
      >
        조건이 변경되었습니다. 다시 생성해 주세요.
      </p>

      <p class="recommend__history-basis">
        과거 {{ result.historyThroughRound }}회차까지 검증된 조합을 제외합니다.
      </p>

      <ol class="recommend__items">
        <li
          v-for="item in result.items"
          :key="item.position"
          class="recommend__item"
        >
          <span class="recommend__item-numbers">{{ item.numbers.join(', ') }}</span>
          <span
            v-if="item.score !== null"
            class="recommend__item-score"
          >
            점수 {{ item.score }}점
          </span>
          <ul
            v-if="item.explanationCodes.length"
            class="recommend__item-codes"
          >
            <li
              v-for="code in item.explanationCodes"
              :key="code"
            >
              {{ explanationLabel(code) }}
            </li>
          </ul>
        </li>
      </ol>
    </section>
  </div>
</template>
