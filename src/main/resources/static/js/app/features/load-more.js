import { byId, qs, qsa, setBusy, setText } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';

/**
 * 목록 "더 보기"(10단계). 기존 페이지 이동은 그대로 두고, JSON API(/api/v1/posts, 새로
 * 만들지 않고 재사용)로 다음 페이지를 불러와 같은 모양의 행을 이어 붙인다.
 *
 * 점진적 향상: 버튼은 서버 렌더링 시 hidden이고, 이 모듈이 로드돼야 보인다(index.html).
 * JS가 없거나 로드에 실패하면 기존 페이지 이동만 남는다.
 *
 * 13단계: 더 보기로 불러온 뒤에도 pager(페이지 번호·"다음"·상태 문구)를 실제로 붙인
 * 범위에 맞춰 갱신한다 — 예전에는 pager가 서버가 처음 그린 1페이지에 멈춰 있어, 두세 번
 * 더 눌러도 "다음"을 누르면 이미 화면에 있는 페이지로 다시 이동했다. 정렬 기준에 따라
 * 순서가 바뀔 수 있는 데이터(조회순 등)에 대비해 이미 붙은 글 id는 건너뛴다(중복 방지).
 */
export function init() {
    const button = /** @type {HTMLButtonElement | null} */ (byId('btn-load-more'));
    const status = byId('load-more-status');
    const list = qs('.post-list');
    if (!button || !status || !list) {
        return;
    }

    // 서버가 처음 그린 페이지(0-기반) — 이후 갱신하는 pager 상태 문구의 시작 값이다.
    const startPage = Number(button.dataset.nextPage ?? '1') - 1;
    const seenIds = new Set(
        qsa('.post-list__item[data-post-id]', list).map((el) => el.dataset.postId),
    );

    button.hidden = false;
    button.addEventListener('click', () => loadMore(button, status, list, startPage, seenIds));
}

async function loadMore(button, status, list, startPage, seenIds) {
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

    const newPosts = result.content.filter((post) => !seenIds.has(String(post.id)));
    newPosts.forEach((post) => seenIds.add(String(post.id)));
    const rows = newPosts.map(buildRow);
    rows.forEach((row) => list.appendChild(row));
    setBusy(button, false);

    updatePager(startPage, result.page, result.last);

    if (result.last) {
        button.hidden = true;
    } else {
        button.dataset.nextPage = String(result.page + 1);
    }

    // 방금 붙은 첫 행의 제목으로 포커스를 옮긴다 — 화면이 아래로 늘어났다는 사실과 위치를
    // 함께 알린다. 새로 붙은 행이 없으면(전부 중복이거나, last=false인데 빈 페이지를 준 극단
    // 적인 경우) 포커스를 건드리지 않고 상태 문구로만 알린다 — 포커스 이동과 문구 갱신
    // 순서를 지켜야(먼저 옮기고 나중에 알리면 aria-live 발화가 겹쳐 잘릴 수 있다) 둘 다
    // 온전히 전달된다.
    if (rows.length > 0) {
        rows[0].querySelector('.post-list__title')?.focus();
        setText(status, result.last
            ? '마지막 글까지 모두 불러왔습니다.'
            : `게시글 ${rows.length}개를 더 불러왔습니다.`);
    } else {
        setText(status, result.last
            ? '마지막 글까지 모두 불러왔습니다.'
            : '새 게시글이 없습니다.');
    }
}

/**
 * "더 보기"로 실제 화면에 붙은 범위(startPage~lastLoadedPage)를 pager에 반영한다. pager
 * 자체가 없는 화면(전체 1페이지)에서는 아무 것도 하지 않는다.
 */
function updatePager(startPage, lastLoadedPage, isLast) {
    const pager = qs('nav.pager');
    if (!pager) {
        return;
    }

    const totalPages = Number(pager.dataset.totalPages ?? '0');
    updateStatusText(pager, startPage, lastLoadedPage, totalPages);
    markLoadedPageNumbers(pager, startPage, lastLoadedPage);
    updateNextStep(pager, lastLoadedPage, isLast);
}

function updateStatusText(pager, startPage, lastLoadedPage, totalPages) {
    const statusEl = qs('.pager__status', pager);
    if (!statusEl) {
        return;
    }
    const start = startPage + 1;
    const end = lastLoadedPage + 1;
    statusEl.textContent = start === end ? `${start} / ${totalPages}` : `${start}–${end} / ${totalPages}`;
}

/** 번호 링크 중 이미 화면에 붙은 페이지는 링크를 없애고(다시 눌러도 갈 곳이 없다) 현재
 * 구간으로 표시한다. aria-current는 방금 불러온 마지막 페이지에만 남긴다(동시에 여러 곳에
 * 붙이면 스크린 리더에 "현재 위치"가 여러 개로 들린다). */
function markLoadedPageNumbers(pager, startPage, lastLoadedPage) {
    qsa('.pager__page[data-page]', pager).forEach((el) => {
        const page = Number(el.dataset.page);
        if (page < startPage || page > lastLoadedPage) {
            return;
        }
        let span = el;
        if (el.tagName === 'A') {
            span = document.createElement('span');
            span.className = 'pager__page is-current';
            span.dataset.page = String(page);
            span.textContent = el.textContent;
            el.replaceWith(span);
        } else {
            span.classList.add('is-current');
        }
        if (page === lastLoadedPage) {
            span.setAttribute('aria-current', 'page');
        } else {
            span.removeAttribute('aria-current');
        }
    });
}

/** "다음" 링크의 목적지를 마지막으로 불러온 다음 페이지로 옮긴다(q·category·sort는 그대로
 * 둔 채 page만 바꾼다). 더 불러올 페이지가 없으면 기존 "이전 없음"과 같은 비활성 모양으로
 * 바꾼다. */
function updateNextStep(pager, lastLoadedPage, isLast) {
    const nextEl = qs('[data-role="next"]', pager);
    if (!nextEl) {
        return;
    }
    if (isLast) {
        if (nextEl.tagName === 'A') {
            const span = document.createElement('span');
            span.className = 'pager__step is-disabled';
            span.dataset.role = 'next';
            span.setAttribute('aria-hidden', 'true');
            span.textContent = nextEl.textContent;
            nextEl.replaceWith(span);
        }
        return;
    }
    if (nextEl.tagName === 'A') {
        const url = new URL(nextEl.href, window.location.href);
        url.searchParams.set('page', String(lastLoadedPage + 1));
        nextEl.setAttribute('href', `${url.pathname}${url.search}`);
    }
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
    li.dataset.postId = String(post.id);

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
