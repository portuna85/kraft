// 자동 임시 저장 composable의 flush 동작 테스트. `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { reactive } from 'vue';
import { useDraftAutosave } from './useDraftAutosave.js';
import { readDraft } from './draftStorage.js';

function fakeStorage() {
    const map = new Map();
    return {
        getItem: (key) => (map.has(key) ? map.get(key) : null),
        setItem: (key, value) => { map.set(key, value); },
        removeItem: (key) => { map.delete(key); },
    };
}

test('flush는 디바운스를 기다리지 않고 대기 중인 초안을 바로 저장한다', () => {
    const storage = fakeStorage();
    const draft = reactive({ title: '제목', content: '' });
    const autosave = useDraftAutosave('k', draft, { storage });

    autosave.schedule();
    assert.equal(readDraft(storage, 'k', 1000), null);

    draft.content = '마지막에 친 내용';
    autosave.flush();

    assert.equal(readDraft(storage, 'k', 1000)?.content, '마지막에 친 내용');
});

test('대기 중인 저장이 없으면 flush는 아무것도 쓰지 않는다', () => {
    const storage = fakeStorage();
    const autosave = useDraftAutosave('k', reactive({ title: 'x' }), { storage });

    autosave.flush();

    assert.equal(readDraft(storage, 'k', 1000), null);
});

test('discard 뒤의 flush는 지운 초안을 되살리지 않는다', () => {
    const storage = fakeStorage();
    const autosave = useDraftAutosave('k', reactive({ title: 'x' }), { storage });

    autosave.schedule();
    autosave.discard();
    autosave.flush();

    assert.equal(readDraft(storage, 'k', 1000), null);
});
