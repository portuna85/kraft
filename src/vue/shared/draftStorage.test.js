// 자동 임시 저장 저장소의 순수 로직 테스트. `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { clearDraft, readDraft, writeDraft } from './draftStorage.js';

/** window.localStorage를 흉내 낸 메모리 저장소. */
function fakeStorage(initial = {}) {
    const map = new Map(Object.entries(initial));
    return {
        getItem: (key) => (map.has(key) ? map.get(key) : null),
        setItem: (key, value) => { map.set(key, value); },
        removeItem: (key) => { map.delete(key); },
        _map: map,
    };
}

test('아무것도 저장돼 있지 않으면 null을 반환한다', () => {
    const storage = fakeStorage();
    assert.equal(readDraft(storage, 'k', 1000), null);
});

test('저장한 값을 그대로 읽을 수 있다', () => {
    const storage = fakeStorage();
    writeDraft(storage, 'k', { title: '제목', content: '내용' });
    const result = readDraft(storage, 'k', 1000 * 60);
    assert.equal(result.title, '제목');
    assert.equal(result.content, '내용');
    // savedAt은 내부 관리용이라 읽은 결과에는 노출하지 않는다.
    assert.equal('savedAt' in result, false);
});

test('ttl을 넘긴 초안은 null을 반환하고 저장소에서 지운다', () => {
    const storage = fakeStorage();
    const old = JSON.stringify({ savedAt: Date.now() - 10_000, title: '오래된 글' });
    storage.setItem('k', old);

    assert.equal(readDraft(storage, 'k', 5_000), null);
    assert.equal(storage.getItem('k'), null);
});

test('ttl 안에 있으면 지우지 않는다', () => {
    const storage = fakeStorage();
    const fresh = JSON.stringify({ savedAt: Date.now() - 1_000, title: '최근 글' });
    storage.setItem('k', fresh);

    const result = readDraft(storage, 'k', 5_000);
    assert.equal(result.title, '최근 글');
    assert.notEqual(storage.getItem('k'), null);
});

test('JSON으로 파싱할 수 없는 값은 null을 반환한다(지우지는 않는다)', () => {
    const storage = fakeStorage({ k: '{이건 JSON이 아니다' });
    assert.equal(readDraft(storage, 'k', 1000), null);
    // 형식이 깨진 값은 savedAt을 알 수 없어 만료 판단을 할 수 없으므로 건드리지 않는다.
    assert.notEqual(storage.getItem('k'), null);
});

test('savedAt이 없는 값은 null을 반환한다', () => {
    const storage = fakeStorage({ k: JSON.stringify({ title: '시각 정보 없음' }) });
    assert.equal(readDraft(storage, 'k', 1000), null);
});

test('clearDraft는 저장된 값을 지운다', () => {
    const storage = fakeStorage();
    writeDraft(storage, 'k', { title: '제목' });
    clearDraft(storage, 'k');
    assert.equal(readDraft(storage, 'k', 1000 * 60), null);
});

test('storage 접근이 예외를 던져도(프라이빗 모드 등) 조용히 실패한다', () => {
    const throwing = {
        getItem() { throw new Error('접근 불가'); },
        setItem() { throw new Error('접근 불가'); },
        removeItem() { throw new Error('접근 불가'); },
    };

    assert.doesNotThrow(() => writeDraft(throwing, 'k', { title: '제목' }));
    assert.doesNotThrow(() => clearDraft(throwing, 'k'));
    assert.equal(readDraft(throwing, 'k', 1000), null);
});
