import { byId, qs, setBusy, setText } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';

/**
 * 목록 "더 보기"(10단계). 기존 페이지 이동은 그대로 두고, JSON API(/api/v1/posts, 새로
 * 만들지 않고 재사용)로 다음 페이지를 불러와 같은 모양의 행을 이어 붙인다.
 *
 * 점진적 향상: 버튼은 서버 렌더링 시 hidden이고, 이 모듈이 로드돼야 보인다(index.html).
 * JS가 없거나 로드에 실패하면 기존 페이지 이동만 남는다.
 */
export function init() {
    const button = /** @type {HTMLButtonElement | null} */ (byId('btn-load-more'));
    const status = byId('load-more-status');
    const list = qs('.post-list');
    if (!button || !status || !list) {
        return;
    }

    button.hidden = false;
    button.addEventListener('click', () => loadMore(button, status, list));
}

async function loadMore(button, status, list) {
    if (button.disabled) {
        return;
    }
    setBusy(button, true);
    status.classList.remove('is-error');
    setText(status, '불러오는 중…');

    const params = new URLSearchParams({ page: button.dataset.nextPage ?? '' });
    if (button.dataset.q) {
        params.set('q', button.dataset.q);
    }
    if (button.dataset.category) {
        params.set('category', button.dataset.category);
    }
    if (button.dataset.sort) {
        params.set('sort', button.dataset.sort);
    }

    let result;
    try {
        result = await api.get(`/api/v1/posts?${params.toString()}`);
    } catch (error) {
        setBusy(button, false);
        status.classList.add('is-error');
        setText(status, `게시글을 더 불러오지 못했습니다. ${messageOf(error)} 버튼을 다시 눌러 재시도할 수 있습니다.`);
        return;
    }

    const rows = result.content.map(buildRow);
    rows.forEach((row) => list.appendChild(row));
    setBusy(button, false);

    if (result.last) {
        button.hidden = true;
        setText(status, '마지막 글까지 모두 불러왔습니다.');
    } else {
        button.dataset.nextPage = String(result.page + 1);
        setText(status, `게시글 ${rows.length}개를 더 불러왔습니다.`);
    }

    // 방금 붙은 첫 행의 제목으로 포커스를 옮긴다 — 화면이 아래로 늘어났다는 사실과 위치를
    // 함께 알린다. 붙인 행이 없으면(서버가 last=false인데 빈 페이지를 준 극단적인 경우)
    // 건드리지 않는다.
    rows[0]?.querySelector('.post-list__title')?.focus();
}

/** 목록 select(#search-category)의 옵션에서 분류 코드(예: FREE) → 표시 이름을 찾는다. */
function categoryTitle(value) {
    const option = /** @type {HTMLOptionElement | null} */ (
        qs(`#search-category option[value="${cssEscape(value)}"]`)
    );
    return option ? option.textContent : value;
}

function cssEscape(value) {
    return window.CSS?.escape ? window.CSS.escape(value) : value.replace(/["\\]/g, '\\$&');
}

/** "2026-09-26T18:05:00" → "2026.09.26 18:05". SSR의 #temporals.format(...)과 같은 모양. */
function formatDate(iso) {
    if (!iso) {
        return '';
    }
    const [date, time] = iso.slice(0, 16).split('T');
    return `${date.replaceAll('-', '.')} ${time ?? ''}`.trim();
}

/** index.html의 .post-list__item 구조와 같은 모양을 DOM API로만 만든다(innerHTML 사용 안 함). */
function buildRow(post) {
    const li = document.createElement('li');
    li.className = 'post-list__item';

    const no = document.createElement('span');
    no.className = 'post-list__no';
    const noLabel = document.createElement('span');
    noLabel.className = 'post-list__no-label';
    noLabel.textContent = '#';
    const noValue = document.createElement('span');
    noValue.textContent = String(post.id);
    no.append(noLabel, noValue);

    const link = document.createElement('a');
    link.className = 'post-list__title';
    link.href = `/posts/update/${post.id}`;
    const categorySpan = document.createElement('span');
    categorySpan.className = 'post-list__category';
    categorySpan.textContent = categoryTitle(post.category);
    const titleSpan = document.createElement('span');
    titleSpan.textContent = post.title;
    link.append(categorySpan, titleSpan);

    const meta = document.createElement('span');
    meta.className = 'post-list__meta';
    const author = document.createElement('span');
    author.className = 'post-list__author';
    author.textContent = post.author;
    const date = document.createElement('span');
    date.className = 'post-list__date';
    date.textContent = formatDate(post.modifiedDate);
    meta.append(author, date);

    const views = document.createElement('span');
    views.className = 'post-list__views';
    const viewsHidden = document.createElement('span');
    viewsHidden.className = 'visually-hidden';
    viewsHidden.textContent = '조회 ';
    const viewsValue = document.createElement('span');
    viewsValue.textContent = String(post.viewCount);
    views.append(viewsHidden, viewsValue);

    const comments = document.createElement('span');
    comments.className = 'post-list__comments';
    const commentsHidden = document.createElement('span');
    commentsHidden.className = 'visually-hidden';
    commentsHidden.textContent = '댓글 ';
    const commentsValue = document.createElement('span');
    commentsValue.textContent = String(post.commentCount);
    comments.append(commentsHidden, commentsValue);

    li.append(no, link, meta, views, comments);
    return li;
}
