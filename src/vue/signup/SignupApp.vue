<script setup>
import { reactive, ref } from 'vue';
import { api, messageOf } from '@core/http.js';
import { API, PASSWORD } from '@core/constants.js';
import * as flash from '@ui/flash.js';
import { usePasswordConfirm } from '../shared/usePasswordConfirm.js';
import { useFieldErrors } from '../shared/useFieldErrors.js';

/**
 * 회원가입 폼.
 *
 * 예전에는 "가입하기"가 type=button이고 JS가 click에만 걸려 있어, Enter로 제출할 수도 없고
 * required·type=email 같은 브라우저 기본 검증도 전혀 걸리지 않았다.
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
const saving = ref(false);

const { confirmError, confirmInput, validateMatch } =
    usePasswordConfirm(() => form.password, () => form.passwordConfirm);
const { fieldErrors, apply: applyFieldErrors, clearOnEdit } = useFieldErrors();
clearOnEdit(form);

/** @type {import('vue').Ref<HTMLInputElement|null>} */
const nameInput = ref(null);
/** @type {import('vue').Ref<HTMLInputElement|null>} */
const emailInput = ref(null);
/** @type {import('vue').Ref<HTMLInputElement|null>} */
const passwordInput = ref(null);

async function onSubmit() {
    if (saving.value) {
        return;
    }

    if (!(await validateMatch())) {
        return;
    }

    saving.value = true;
    // 버튼이 비활성화되는 동안에도 입력란 자체는 잠그지 않으므로, 응답을 기다리는 사이
    // 사용자가 값을 고치더라도 이번 요청은 제출 시점 스냅샷을 그대로 쓴다.
    const snapshot = { name: form.name, email: form.email, password: form.password };
    try {
        await api.post(API.USERS, {
            name: snapshot.name,
            email: snapshot.email,
            password: snapshot.password,
        });
        flash.set('SIGNUP_DONE');
        window.location.href = '/login';
    } catch (error) {
        // 필드별 오류가 있으면 입력칸 옆에서 알린다. 이메일 중복처럼 필드 하나로
        // 좁혀지지 않는 서버 판정에는 errors가 없으므로 그때만 배너로 보여준다.
        const handledByField = await applyFieldErrors(error, {
            name: nameInput, email: emailInput, password: passwordInput,
        });
        if (!handledByField) {
            flash.showError(messageOf(error));
        }
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
      <!-- 이 값은 게시글·댓글 작성자로 누구에게나 보인다. 예전
           라벨 "이름"은 실명을 넣으라는 뜻으로 읽힐 수 있었다. -->
      <label for="name">공개 닉네임</label>
      <input
        id="name"
        ref="nameInput"
        v-model.trim="form.name"
        type="text"
        class="form-control"
        :class="{ 'is-invalid': fieldErrors.name }"
        :aria-invalid="fieldErrors.name ? 'true' : undefined"
        placeholder="다른 사람에게 보일 닉네임"
        maxlength="50"
        aria-describedby="name-help name-error"
        required
      >
      <small
        id="name-help"
        class="form-text text-muted"
      >게시글·댓글 작성자로 모든 방문자에게 표시됩니다. 실명 대신 닉네임을 권장합니다. 탈퇴하면 '탈퇴한 사용자'로 바뀝니다.</small>
      <div
        id="name-error"
        class="invalid-feedback"
      >
        {{ fieldErrors.name }}
      </div>
    </div>
    <div class="mb-3">
      <label for="email">이메일</label>
      <input
        id="email"
        ref="emailInput"
        v-model.trim="form.email"
        type="email"
        class="form-control"
        :class="{ 'is-invalid': fieldErrors.email }"
        :aria-invalid="fieldErrors.email ? 'true' : undefined"
        placeholder="이메일을 입력하세요"
        maxlength="100"
        autocomplete="email"
        aria-describedby="email-error"
        required
      >
      <div
        id="email-error"
        class="invalid-feedback"
      >
        {{ fieldErrors.email }}
      </div>
    </div>
    <div class="mb-3">
      <label for="password">비밀번호 ({{ PASSWORD.HINT }})</label>
      <input
        id="password"
        ref="passwordInput"
        v-model="form.password"
        type="password"
        class="form-control"
        :class="{ 'is-invalid': fieldErrors.password }"
        :aria-invalid="fieldErrors.password ? 'true' : undefined"
        placeholder="비밀번호를 입력하세요"
        autocomplete="new-password"
        :minlength="PASSWORD.MIN_LENGTH"
        :maxlength="PASSWORD.MAX_LENGTH"
        aria-describedby="password-error"
        required
      >
      <div
        id="password-error"
        class="invalid-feedback"
      >
        {{ fieldErrors.password }}
      </div>
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
        :aria-invalid="confirmError ? 'true' : undefined"
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
