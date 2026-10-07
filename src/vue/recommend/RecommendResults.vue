<script setup>
import { onBeforeUnmount, ref } from 'vue';
import { showToast } from '@ui/toast.js';
import { ballColorClass } from './ballColor.js';
import { copyText } from './copyText.js';
import { formatAll, formatCombination, letterOf } from './recommendRequest.js';
import RecommendIcon from './RecommendIcon.vue';

/**
 * 번호 추천 화면의 오른쪽 카드("02 / 결과"). 제목(h2)은 결과가 없을 때도 항상 있다 — 성공 후 포커스가 그리로
 * 옮겨 가고, 스크린리더 사용자가 결과 영역을 찾는 기준이 된다. 알림(이력 준비 중·오류)과 이전 결과가 함께
 * 있을 수 있다: 재시도가 실패해도 이전 결과는 남는다.
 */
const props = defineProps({
    result: {
        type: /** @type {import('vue').PropType<import('./useRecommendation.js').RecommendationResponse | null>} */ (Object),
        default: null,
    },
    status: { type: String, required: true },
    errorMessage: { type: /** @type {import('vue').PropType<string | null>} */ (String), default: null },
});

const heading = ref(/** @type {HTMLElement | null} */ (null));
defineExpose({ focusHeading: () => heading.value?.focus() });

/** 방금 복사한 항목(행 인덱스 또는 'all'). 아이콘을 잠깐 체크로 바꿔 보여준다. */
const copied = ref(/** @type {number | 'all' | null} */ (null));
let resetTimer = 0;
onBeforeUnmount(() => window.clearTimeout(resetTimer));

/**
 * 클릭 핸들러 안에서 바로 복사한다(클립보드는 사용자 제스처가 필요하다). 성공·실패는 토스트가 알린다 —
 * 토스트가 이미 aria-live 영역이라 따로 낭독 영역을 두지 않는다(이중 낭독 방지).
 *
 * @param {string} text
 * @param {number | 'all'} key
 * @param {string} doneMessage
 */
async function copy(text, key, doneMessage) {
    const ok = await copyText(text);
    if (!ok) {
        showToast('복사하지 못했습니다. 번호를 직접 선택해 복사해 주세요.', 'danger');
        return;
    }
    copied.value = key;
    window.clearTimeout(resetTimer);
    resetTimer = window.setTimeout(() => {
        copied.value = null;
    }, 1500);
    showToast(doneMessage, 'success');
}

const copyRow = (item, index) =>
    copy(formatCombination(item.numbers), index, `${letterOf(index)} 조합을 복사했습니다.`);
const copyEverything = () => copy(formatAll(props.result?.items ?? []), 'all', '전체 조합을 복사했습니다.');
</script>

<template>
  <section
    class="card-kraft recommend-card"
    aria-labelledby="recommend-results-title"
  >
    <div class="recommend-card__head">
      <div>
        <p
          class="recommend-card__step"
          aria-hidden="true"
        >
          02 / 결과
        </p>
        <h2
          id="recommend-results-title"
          ref="heading"
          class="recommend-card__title"
          tabindex="-1"
        >
          추천 결과
        </h2>
      </div>
      <button
        v-if="result"
        id="btn-recommend-copy-all"
        type="button"
        class="btn btn-sm recommend__copy-all"
        @click="copyEverything"
      >
        <RecommendIcon :name="copied === 'all' ? 'check' : 'copy'" />
        전체 복사
      </button>
    </div>

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

    <template v-if="result">
      <ol class="recommend__items">
        <li
          v-for="(item, index) in result.items"
          :key="item.position"
          class="recommend__item"
        >
          <span
            class="recommend__letter"
            aria-hidden="true"
          >{{ letterOf(index) }}</span>
          <span class="visually-hidden">추천 {{ letterOf(index) }}: {{ formatCombination(item.numbers) }}</span>
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
          <button
            type="button"
            class="btn btn-sm recommend__copy"
            :aria-label="`${letterOf(index)} 조합 복사`"
            @click="copyRow(item, index)"
          >
            <RecommendIcon :name="copied === index ? 'check' : 'copy'" />
          </button>
        </li>
      </ol>

      <p class="recommend__history-basis">
        <RecommendIcon name="check" />
        1~{{ result.historyThroughRound }}회 1등 당첨 조합을 제외했습니다.
      </p>
    </template>

    <div
      v-else
      class="recommend__empty"
    >
      <span
        class="recommend__empty-tile"
        aria-hidden="true"
      >
        <RecommendIcon name="shuffle" />
      </span>
      <p class="recommend__empty-title">
        새로운 조합을 기다리고 있어요
      </p>
      <p class="recommend__empty-hint">
        추천 방식과 개수를 고르고<br>번호 추천받기를 눌러보세요.
      </p>
    </div>
  </section>
</template>
