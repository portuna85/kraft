<script setup>
import { nextTick, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import * as flash from '@ui/flash.js';

/**
 * 비밀번호 재설정 링크 요청.
 *
 * 성공·실패를 가리지 않고 <b>같은 안내</b>를 보여준다. 가입되지 않은 주소라고 알려주면
 * 그것만으로 "이 주소가 가입되어 있다"를 확인하는 도구가 되기 때문이다. 서버도 같은 이유로
 * 항상 204를 준다(PasswordResetService).
 *
 * 입력 형식 오류(400)만은 그대로 보여준다 — 그것은 계정 정보가 아니라 사용자가 고칠 입력이다.
 */
const email = ref('');
const sending = ref(false);
const sent = ref(false);
const doneHeading = ref(null);

async function onSubmit() {
    sending.value = true;
    try {
        await api.post('/api/v1/users/password-reset', { email: email.value });
        sent.value = true;
        // 폼이 사라지고 완료 안내로 바뀐다 — role="status"만으로는 스크린리더가 그 순간
        // 읽어주지 않을 수 있어(개선 보고서 F09), 게시글 추천/추천 결과와 같은 패턴으로
        // 안내 문단에 포커스를 옮긴다.
        await nextTick();
        doneHeading.value?.focus();
    } catch (error) {
        flash.showError(messageOf(error));
    } finally {
        sending.value = false;
    }
}
</script>

<template>
  <div
    v-if="sent"
    id="forgot-password-done"
    role="status"
  >
    <p
      ref="doneHeading"
      tabindex="-1"
    >
      가입된 주소라면 재설정 링크를 보냈습니다. 메일함을 확인해 주세요.
    </p>
    <p class="text-muted">
      링크는 30분 동안 한 번만 사용할 수 있습니다. 메일이 오지 않았다면 주소를 다시 확인해 주세요.
    </p>
    <div class="btn-group-gap">
      <a
        href="/login"
        role="button"
        class="btn btn-secondary"
      >로그인으로</a>
    </div>
  </div>

  <form
    v-else
    id="forgot-password-form"
    @submit.prevent="onSubmit"
  >
    <p class="text-muted">
      가입할 때 쓴 이메일 주소로 비밀번호 재설정 링크를 보내드립니다.
    </p>
    <div class="mb-3">
      <label for="email">이메일</label>
      <input
        id="email"
        v-model.trim="email"
        type="email"
        class="form-control"
        placeholder="이메일을 입력하세요"
        maxlength="100"
        autocomplete="email"
        required
      >
    </div>
    <div class="btn-group-gap">
      <a
        href="/login"
        role="button"
        class="btn btn-secondary"
      >취소</a>
      <button
        id="btn-forgot-password"
        type="submit"
        class="btn btn-primary"
        :disabled="sending"
      >
        링크 받기
      </button>
    </div>
  </form>
</template>
