// 댓글·답글 로컬 상태 규칙의 조합 시나리오(평가 보고서 2026-09-25 F02·F03). `npm run test:unit`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { applyDelete, applyRepliesPage, applyReplyCreated, initReplyCursor, replyAfterId } from './commentState.js';

const range = (from, to) => Array.from({ length: to - from + 1 }, (_, i) => ({ id: from + i }));
const ids = (items) => items.map((r) => r.id);

/**
 * 서버를 흉내 낸다. 부모 100의 답글을 id 오름차순으로 들고 있고, afterId 초과를 20개씩 준다
 * (CommentRepository.findRepliesByParentIdAsc와 같은 keyset 규칙).
 */
function fakeServer(replyIds) {
    const rows = [...replyIds];
    return {
        add(id) { rows.push(id); },
        remove(id) { rows.splice(rows.indexOf(id), 1); },
        page(afterId) {
            const after = afterId === '' ? -Infinity : Number(afterId);
            const rest = rows.filter((id) => id > after);
            return { comments: rest.slice(0, 20).map((id) => ({ id })), hasMore: rest.length > 20 };
        },
        count() { return rows.length; },
    };
}

/** 최초 렌더링과 같은 모양: 부모 1개, 답글 25개 중 앞 20개만 내려온 상태. */
function initialState() {
    const server = fakeServer(range(101, 125).map((r) => r.id));
    const parent = { id: 100, replies: range(101, 120), replyCount: 25, hasMoreReplies: true };
    const comments = [parent];
    comments.forEach(initReplyCursor);
    return { server, parent, comments, deletedIds: new Set(), total: 1 + 25 };
}

test('F02: 새 답글을 쓴 뒤 답글 더 보기로 아직 받지 않은 답글이 빠짐없이·중복 없이 온다', () => {
    const { server, parent, deletedIds } = initialState();

    server.add(126);
    assert.equal(applyReplyCreated(parent, { id: 126 }), true);
    assert.equal(replyAfterId(parent), '120', '로컬 답글은 커서를 움직이지 않는다');

    const page = server.page(replyAfterId(parent));
    applyRepliesPage(parent, page.comments, page.hasMore, deletedIds);

    assert.deepEqual(ids(parent.replies), ids(range(101, 126)));
    assert.equal(parent.hasMoreReplies, false);
    assert.equal(parent.replyCount, 26);
});

test('F03: 답글 추가 → 답글 삭제 → 부모 삭제에서 매 단계 개수가 서버와 같고 마지막은 0이다', () => {
    const { server, parent, comments, deletedIds } = initialState();
    let total = 26;

    server.add(126);
    applyReplyCreated(parent, { id: 126 });
    total += 1;
    assert.equal(total, 1 + server.count());

    server.remove(110);
    total -= applyDelete(comments, 110, deletedIds);
    assert.equal(parent.replyCount, server.count());
    assert.equal(total, 1 + server.count());

    // 같은 삭제 알림이 한 번 더 와도 다시 빼지 않는다.
    assert.equal(applyDelete(comments, 110, deletedIds), 0);

    total -= applyDelete(comments, 100, deletedIds);
    assert.equal(total, 0);
    assert.equal(comments.length, 0);
});

test('F02·F03: 답글 더 보기 응답이 늦게 와도 그 사이 삭제한 답글이 되살아나지 않고 개수도 그대로다', () => {
    const { server, parent, comments, deletedIds } = initialState();
    let total = 26;

    server.add(126);
    applyReplyCreated(parent, { id: 126 });
    total += 1;

    // 요청을 보낸 시점의 응답(126 포함)을 미리 만들어 두고, 도착 전에 126을 지운다.
    const inFlight = server.page(replyAfterId(parent));
    server.remove(126);
    total -= applyDelete(comments, 126, deletedIds);

    applyRepliesPage(parent, inFlight.comments, inFlight.hasMore, deletedIds);

    assert.deepEqual(ids(parent.replies), ids(range(101, 125)));
    assert.equal(parent.replyCount, 25);
    assert.equal(total, 1 + server.count());
});

test('F02: 로드된 마지막 답글을 지워도 커서가 뒤로 가지 않아 같은 답글을 다시 받지 않는다', () => {
    const { server, parent, comments, deletedIds } = initialState();

    server.remove(120);
    applyDelete(comments, 120, deletedIds);
    assert.equal(replyAfterId(parent), '120');

    const page = server.page(replyAfterId(parent));
    applyRepliesPage(parent, page.comments, page.hasMore, deletedIds);
    assert.deepEqual(ids(parent.replies), [...ids(range(101, 119)), ...ids(range(121, 125))]);
});

test('초기 답글이 없는 부모는 처음부터 받는다', () => {
    const parent = { id: 1, replies: [], replyCount: 0, hasMoreReplies: false };
    initReplyCursor(parent);
    assert.equal(replyAfterId(parent), '');
});
