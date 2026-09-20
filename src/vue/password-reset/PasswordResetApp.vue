<script setup>
import { nextTick, reactive, ref, watch } from 'vue';
import { api, messageOf } from '@core/http.js';
import * as flash from '@ui/flash.js';

/**
 * 메일 링크로 들어온 사람이 새 비밀번호를 정하는 화면.
 *
 * 토큰은 서버가 data 속성으로 건넨 값을 그대로 돌려보낸다. 유효한지·만료됐는지는 저장 요청에서
 * 서버가 판정한다 — 화면을 여는 것만으로 토큰이 소모되면 메일 미리보기나 링크 검사기가 대신
 * 눌러 버릴 수 있다.
 *
 * 확인란 불일치는 가입 화면과 같은 규칙으로 필드 옆에서 알린다.
 */
const props = defineProps({
    token: { type: String, required: true },
});

const form = reactive({ newPassword: '', confirm: '' });
const confirmError = ref('');
const saving = ref(false);

const confirmInput = ref(null);

// SignupApp.vue와 같은 이유(개선 보고서 F13) — 값이 다시 같아지는 순간 바로 지운다.
watch([() => form.newPassword, () => form.confirm], () => {
    if (confirmError.value && form.newPassword === form.confirm) {
        confirmError.value = '';
    }
});

async function onSubmit() {
    confirmError.value = '';

    if (form.newPassword !== form.confirm) {
        confirmError.value = '비밀번호가 일치하지 않습니다.';
        await nextTick();
        confirmInput.value?.focus();
        return;
    }

    saving.value = true;
    try {
        await api.post('/api/v1/users/password-reset/confirm', {
            token: props.token,
            newPassword: form.newPassword,
        });
        // 재설정은 이 계정의 모든 세션을 폐기한다. 새 비밀번호로 다시 들어오면 된다.
        flash.set('PASSWORD_RESET');
        window.location.href = '/login';
    } catch (error) {
        // 만료·이미 쓴 링크도 여기로 온다. 서버 문구에 다시 요청하라는 안내가 들어 있다.
        flash.showError(messageOf(error));
        saving.value = false;
    }
}
</script>

<template>
  <form
    id="password-reset-form"
    @submit.prevent="onSubmit"
  >
    <div class="mb-3">
      <label for="newPassword">새 비밀번호 (8자 이상 72자 이하, 대문자·소문자·특수문자 포함)</label>
      <input
        id="newPassword"
        v-model="form.newPassword"
        type="password"
        class="form-control"
        placeholder="새 비밀번호를 입력하세요"
        autocomplete="new-password"
        minlength="8"
        maxlength="72"
        required
      >
    </div>
    <div class="mb-3">
      <label for="newPasswordConfirm">새 비밀번호 확인</label>
      <input
        id="newPasswordConfirm"
        ref="confirmInput"
        v-model="form.confirm"
        type="password"
        class="form-control"
        :class="{ 'is-invalid': confirmError }"
        :aria-invalid="confirmError ? 'true' : null"
        placeholder="새 비밀번호를 다시 입력하세요"
        autocomplete="new-password"
        aria-describedby="newPasswordConfirm-error"
        required
      >
      <div
        id="newPasswordConfirm-error"
        class="invalid-feedback"
      >
        {{ confirmError }}
      </div>
    </div>

    <p class="text-muted">
      바꾸고 나면 이 계정의 모든 기기에서 로그아웃됩니다.
    </p>

    <div class="btn-group-gap">
      <a
        href="/login"
        role="button"
        class="btn btn-secondary"
      >취소</a>
      <button
        id="btn-password-reset"
        type="submit"
        class="btn btn-primary"
        :disabled="saving"
      >
        비밀번호 변경
      </button>
    </div>
  </form>
</template>
