import { byId, qs, qsa, setBusy, setText } from '../core/dom.js';
import { api, messageOf } from '../core/http.js';
import { API } from '../core/constants.js';
import { formatDateTime } from '../core/datetime.js';

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
 *
 * A-FE-07: 더 보기로 불러온 행은 DOM에만 있고 URL·기록에 흔적이 없어, 글을 열었다가
 * 뒤로 가면(bfcache가 없는 경우 — 있으면 DOM이 그대로 남아 이 모듈 자체가 다시 실행되지
 * 않는다) 서버가 처음 그린 1페이지로 돌아가 다시 눌러야 했다. 불러온 글 데이터를
 * sessionStorage에 같은 검색 조건 키로 남겨 두고, 이 모듈이 다시 실행될 때(=bfcache가
 * 아닌 새 렌더) 있으면 네트워크 없이 그대로 재생한다 — 조회수 등 최신값과 살짝 다를 수
 * 있지만, "다시 눌러야 하는" 불편보다 낫다.
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

    // 점진적 향상 노출이 먼저다 — restoreLoadedState가 "이미 마지막 페이지까지 불러온
    // 상태"를 복원하면서 다시 숨길 수 있는데, 그 뒤에 여기서 무조건 드러내면 그 숨김이
    // 곧바로 지워진다.
    button.hidden = false;
    restoreLoadedState(button, status, list, startPage, seenIds);

    button.addEventListener('click', () => loadMore(button, status, list, startPage, seenIds));
}

/** 같은 검색·분류·정렬 조건을 가리키는 안정된 키. page는 일부러 뺀다 — 복원 대상은
 * "이 조건의 목록에서 얼마나 더 불러왔는가"이지 특정 페이지 번호가 아니다. */
function storageKeyFor(button) {
    const parts = ['q', 'category', 'sort', 'scope'].map((name) => `${name}=${button.dataset[name] ?? ''}`);
    return `kraft:load-more:${parts.join('&')}`;
}

/** sessionStorage 접근 자체가 막힌 환경(프라이빗 모드 등)에서도 더 보기 기본 동작은
 * 그대로 되어야 한다 — 저장·복원 모두 실패를 조용히 삼킨다(draftStorage.js와 같은 관례). */
function saveLoadedState(button, startPage, loadedPosts, isLast) {
    try {
        window.sessionStorage.setItem(storageKeyFor(button), JSON.stringify({
            startPage,
            posts: loadedPosts,
            nextPage: button.dataset.nextPage,
            isLast,
        }));
    } catch {
        // 복원은 그냥 못 하게 될 뿐, 더 보기 자체의 성공에는 영향이 없다.
    }
}

function restoreLoadedState(button, status, list, startPage, seenIds) {
    let saved;
    try {
        const raw = window.sessionStorage.getItem(storageKeyFor(button));
        saved = raw ? JSON.parse(raw) : null;
    } catch {
        return;
    }
    if (!saved || saved.startPage !== startPage) {
        return;
    }

    const restoredPosts = saved.posts.filter((post) => !seenIds.has(String(post.id)));
    if (restoredPosts.length === 0) {
        return;
    }
    restoredPosts.forEach((post) => seenIds.add(String(post.id)));
    restoredPosts.map(buildRow).forEach((row) => list.appendChild(row));

    button.dataset.nextPage = saved.nextPage;
    button.hidden = saved.isLast;
    updatePager(startPage, Number(saved.nextPage) - 1, saved.isLast);
    setText(status, saved.isLast
        ? '마지막 글까지 모두 불러왔습니다.'
        : `게시글 ${restoredPosts.length}개를 이전 상태에서 이어서 불러왔습니다.`);
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
    if (button.dataset.scope) {
        params.set('scope', button.dataset.scope);
    }

    let result;
    try {
        result = await api.get(`${API.POSTS}?${params.toString()}`);
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

    const previouslyLoaded = readLoadedPosts(button);
    saveLoadedState(button, startPage, [...previouslyLoaded, ...newPosts], result.last);

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

/** saveLoadedState가 남긴 이번 조건의 누적 목록. 없거나 읽을 수 없으면 빈 배열이다. */
function readLoadedPosts(button) {
    try {
        const raw = window.sessionStorage.getItem(storageKeyFor(button));
        const saved = raw ? JSON.parse(raw) : null;
        return saved?.posts ?? [];
    } catch {
        return [];
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
    const range = start === end ? `${start}` : `${start}–${end}`;
    // 전체 페이지 수를 모르는 검색 결과(BE-08)는 서버가 data-total-pages를 0으로 둔다 — "/ 전체" 없이
    // 지금까지 본 범위만 알린다.
    statusEl.textContent = totalPages > 0 ? `${range} / ${totalPages}` : `${range}페이지`;
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
    const nextEl = /** @type {HTMLAnchorElement | null} */ (qs('[data-role="next"]', pager));
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
    // index.html과 같은 규칙(A-BE-11): 기본 정렬이 등록순이라 등록일을 보이고, 실제로
    // 수정된 글만 표시를 덧붙인다.
    date.textContent = formatDateTime(post.createdAt) + (post.modified ? ' (수정됨)' : '');
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
