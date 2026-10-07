<script setup>
import { COUNT_OPTIONS } from './recommendRequest.js';
import RecommendIcon from './RecommendIcon.vue';

/**
 * 번호 추천 화면의 왼쪽 카드("01 / 설정"): 추천 개수, 생성 버튼. 상태는 부모(useRecommendation)가 들고
 * 있고, 이 컴포넌트는 고른 값을 v-model로 올리고 제출만 알린다.
 */
defineProps({
    // 서버가 렌더링 시점에 아는 "검증된 이력의 마지막 회차". 없으면 일반 문구로 물러선다.
    historyRound: { type: Number, default: null },
    generating: { type: Boolean, default: false },
});
const count = defineModel('count', { type: Number, required: true });
defineEmits(['submit']);
</script>

<template>
  <section
    class="card-kraft recommend-card"
    aria-labelledby="recommend-settings-title"
  >
    <div class="recommend-card__head">
      <div>
        <p
          class="recommend-card__step"
          aria-hidden="true"
        >
          01 / 설정
        </p>
        <h2
          id="recommend-settings-title"
          class="recommend-card__title"
        >
          추천 설정
        </h2>
      </div>
      <RecommendIcon
        name="shuffle"
        class="recommend-card__mark"
      />
    </div>

    <form
      class="recommend__form"
      @submit.prevent="$emit('submit')"
    >
      <div class="recommend__field">
        <label
          class="recommend__label"
          for="recommend-count"
        >추천 개수</label>
        <select
          id="recommend-count"
          v-model.number="count"
          class="form-select"
        >
          <option
            v-for="n in COUNT_OPTIONS"
            :key="n"
            :value="n"
          >
            {{ n }}개 조합
          </option>
        </select>
      </div>

      <div class="recommend__info">
        <RecommendIcon name="shield" />
        <p>
          <span v-if="historyRound">1~{{ historyRound }}회 1등 당첨 조합 제외</span>
          <span v-else>역대 1등 당첨 조합 제외</span>
          <small>보너스 번호를 제외한 6개 번호가 같은 조합 기준</small>
        </p>
      </div>

      <button
        id="btn-recommend-generate"
        type="submit"
        class="btn btn-primary recommend__cta"
        :disabled="generating"
      >
        <RecommendIcon name="shuffle" />
        {{ generating ? '생성 중…' : '번호 추천받기' }}
      </button>

      <p
        v-if="generating"
        class="form-progress"
        aria-hidden="true"
      >
        추천 번호를 생성하는 중입니다.
      </p>

      <p class="recommend__disclaimer">
        과거 당첨 조합을 제외해도 개별 조합의 당첨 확률은 달라지지 않습니다.
      </p>
    </form>
  </section>
</template>
