// useFieldErrors의 순수 로직 테스트. `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ref } from 'vue';
import { useFieldErrors } from './useFieldErrors.js';

test('errors가 없으면 아무것도 하지 않고 false를 돌려준다', async () => {
    const { fieldErrors, apply } = useFieldErrors();

    const handled = await apply(new Error('no body'));

    assert.equal(handled, false);
    assert.deepEqual({ ...fieldErrors }, {});
});

test('errors[]가 있으면 필드별로 나누고 true를 돌려준다', async () => {
    const { fieldErrors, apply } = useFieldErrors();
    const error = { body: { errors: [{ field: 'title', message: '제목은 필수입니다.' }] } };

    const handled = await apply(error);

    assert.equal(handled, true);
    assert.equal(fieldErrors.title, '제목은 필수입니다.');
});

test('다시 적용하기 전에 이전 오류를 지운다', async () => {
    const { fieldErrors, apply } = useFieldErrors();
    await apply({ body: { errors: [{ field: 'title', message: '이전 오류' }] } });

    await apply({ body: { errors: [{ field: 'content', message: '새 오류' }] } });

    assert.equal(fieldErrors.title, undefined);
    assert.equal(fieldErrors.content, '새 오류');
});

test('첫 오류 필드의 입력에 포커스를 옮긴다', async () => {
    const { apply } = useFieldErrors();
    let focused = false;
    const titleInput = ref({ focus: () => { focused = true; } });
    const error = {
        body: {
            errors: [
                { field: 'content', message: '내용은 필수입니다.' },
                { field: 'title', message: '제목은 필수입니다.' },
            ],
        },
    };

    await apply(error, { title: titleInput });

    assert.equal(focused, true);
});

test('clear()는 모든 필드 오류를 지운다', async () => {
    const { fieldErrors, apply, clear } = useFieldErrors();
    await apply({ body: { errors: [{ field: 'title', message: '오류' }] } });

    clear();

    assert.deepEqual({ ...fieldErrors }, {});
});
