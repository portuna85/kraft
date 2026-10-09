<script setup>
import { reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API, PASSWORD } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { usePasswordConfirm } from '../shared/usePasswordConfirm.js';
import { useFieldErrors } from '../shared/useFieldErrors.js';

/**
 * 메일 링크로 들어온 사람이 새 비밀번호를 정하는 화면.
 *
 * 토큰은 URL 프래그먼트(#token=...)에서 읽어 mount.js가 props로 건넨 값을 그대로 돌려보낸다. 유효한지·만료됐는지는 저장 요청에서
 * 서버가 판정한다 — 화면을 여는 것만으로 토큰이 소모되면 메일 미리보기나 링크 검사기가 대신
 * 눌러 버릴 수 있다.
 *
 * 확인란 불일치 검사는 가입 화면과 같은 규칙을 쓴다 — `usePasswordConfirm`으로 공유한다.
 */
const props = defineProps({
    token: { type: String, default: '' },
});

const form = reactive({ newPassword: '', confirm: '' });
const saving = ref(false);

const { confirmError, confirmInput, validateMatch } =
    usePasswordConfirm(() => form.newPassword, () => form.confirm);
const { fieldErrors, apply: applyFieldErrors, clearOnEdit } = useFieldErrors();
clearOnEdit(form);
/** @type {import('vue').Ref<HTMLInputElement|null>} */
const newPasswordInput = ref(null);

async function onSubmit() {
    if (saving.value) {
        return;
    }

    if (!(await validateMatch())) {
        return;
    }

    saving.value = true;
    const snapshot = { newPassword: form.newPassword };
    try {
        await api.post(`${API.PASSWORD_RESET}/confirm`, {
            token: props.token,
            newPassword: snapshot.newPassword,
        });
        // 재설정은 이 계정의 모든 세션을 폐기한다. 새 비밀번호로 다시 들어오면 된다.
        flash.set('PASSWORD_RESET');
        window.location.href = '/login';
    } catch (error) {
        const handledByField = await applyFieldErrors(error, { newPassword: newPasswordInput });
        if (!handledByField) {
            // 만료·이미 쓴 링크도 여기로 온다. 서버 문구에 다시 요청하라는 안내가 들어 있다.
            flash.showError(messageOf(error));
        }
        saving.value = false;
    }
}
</script>

<template>
  <!-- 링크에 토큰이 없으면 제출해 봐야 실패하므로 처음부터 안내한다. -->
  <div
    v-if="!token"
    id="password-reset-invalid"
    class="alert alert-warning"
    role="alert"
  >
    <p class="mb-2">
      재설정 링크가 올바르지 않습니다. 메일의 링크를 그대로 열었는지 확인하거나, 링크를 다시 요청해 주세요.
    </p>
    <a
      href="/forgot-password"
      class="btn btn-primary"
    >재설정 링크 다시 요청</a>
  </div>
  <form
    v-else
    id="password-reset-form"
    @submit.prevent="onSubmit"
  >
    <div class="mb-3">
      <label for="newPassword">새 비밀번호 ({{ PASSWORD.HINT }})</label>
      <input
        id="newPassword"
        ref="newPasswordInput"
        v-model="form.newPassword"
        type="password"
        class="form-control"
        :class="{ 'is-invalid': fieldErrors.newPassword }"
        :aria-invalid="fieldErrors.newPassword ? 'true' : undefined"
        placeholder="새 비밀번호를 입력하세요"
        autocomplete="new-password"
        :minlength="PASSWORD.MIN_LENGTH"
        :maxlength="PASSWORD.MAX_LENGTH"
        aria-describedby="newPassword-error"
        required
      >
      <div
        id="newPassword-error"
        class="invalid-feedback"
      >
        {{ fieldErrors.newPassword }}
      </div>
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
        :aria-invalid="confirmError ? 'true' : undefined"
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
