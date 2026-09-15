<script setup>
import { nextTick, reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import * as flash from '@ui/flash.js';

/**
 * 회원가입 폼.
 *
 * 예전에는 "가입하기"가 type=button이고 JS가 click에만 걸려 있어, Enter로 제출할 수도 없고
 * required·type=email 같은 브라우저 기본 검증도 전혀 걸리지 않았다(개선 보고서 사용성 항목).
 * 여기서는 진짜 submit을 쓴다 — 브라우저가 필수·형식·길이를 먼저 잡고, 통과한 뒤에야
 * onSubmit이 돈다.
 *
 * 비밀번호 확인 불일치는 서버에 물어볼 필요가 없는 입력 오류라 해당 필드 옆에서 알리고
 * 포커스를 옮긴다. 반면 이메일 중복 같은 서버 판정은 폼 전체에 걸리는 오류라 배너에 띄운다.
 *
 * 이름·이메일은 앞뒤 공백을 떼지만 비밀번호는 그대로 보낸다. 예전 valueOf()는 비밀번호까지
 * 잘라내 사용자가 입력한 것과 다른 값이 저장됐다 — 로그인 폼은 서버로 원문을 보내므로
 * 양쪽이 어긋날 수 있었다.
 */
const form = reactive({
    name: '',
    email: '',
    password: '',
    passwordConfirm: '',
});
const confirmError = ref('');
const saving = ref(false);

const confirmInput = ref(null);

async function onSubmit() {
    confirmError.value = '';

    if (form.password !== form.passwordConfirm) {
        confirmError.value = '비밀번호가 일치하지 않습니다.';
        await nextTick();
        confirmInput.value?.focus();
        return;
    }

    saving.value = true;
    try {
        await api.post('/api/v1/users', {
            name: form.name,
            email: form.email,
            password: form.password,
        });
        flash.set('SIGNUP_DONE');
        window.location.href = '/login';
    } catch (error) {
        flash.showError(messageOf(error));
        saving.value = false;
    }
}
</script>

<template>
  <form
    id="signup-form"
    @submit.prevent="onSubmit"
  >
    <div class="mb-3">
      <label for="name">이름</label>
      <input
        id="name"
        v-model.trim="form.name"
        type="text"
        class="form-control"
        placeholder="이름을 입력하세요"
        maxlength="50"
        required
      >
    </div>
    <div class="mb-3">
      <label for="email">이메일</label>
      <input
        id="email"
        v-model.trim="form.email"
        type="email"
        class="form-control"
        placeholder="이메일을 입력하세요"
        maxlength="100"
        autocomplete="email"
        required
      >
    </div>
    <div class="mb-3">
      <label for="password">비밀번호 (8자 이상, 대문자·소문자·특수문자 포함)</label>
      <input
        id="password"
        v-model="form.password"
        type="password"
        class="form-control"
        placeholder="비밀번호를 입력하세요"
        autocomplete="new-password"
        minlength="8"
        required
      >
    </div>
    <div class="mb-3">
      <label for="passwordConfirm">비밀번호 확인</label>
      <!-- 서버 검증과 어긋나지 않도록 여기에는 minlength를 걸지 않는다. 확인란의 판정 기준은
           "위와 같은가"뿐이고, 길이·복잡도는 password 쪽과 서버가 본다. -->
      <input
        id="passwordConfirm"
        ref="confirmInput"
        v-model="form.passwordConfirm"
        type="password"
        class="form-control"
        :class="{ 'is-invalid': confirmError }"
        :aria-invalid="confirmError ? 'true' : null"
        placeholder="비밀번호를 다시 입력하세요"
        autocomplete="new-password"
        aria-describedby="passwordConfirm-error"
        required
      >
      <div
        id="passwordConfirm-error"
        class="invalid-feedback"
      >
        {{ confirmError }}
      </div>
    </div>

    <div class="btn-group-gap">
      <a
        href="/"
        role="button"
        class="btn btn-secondary"
      >취소</a>
      <button
        id="btn-signup"
        type="submit"
        class="btn btn-primary"
        :disabled="saving"
      >
        가입하기
      </button>
    </div>
  </form>
</template>
