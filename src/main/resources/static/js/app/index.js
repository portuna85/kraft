// src/main/resources/static/js/app/index.js
(function () {
    'use strict';

    const $id = (id) => document.getElementById(id);

    // 공통 HTTP 유틸
    async function http(url, method, data) {
        const headers = {};

        // JSON 본문이 있을 때만 Content-Type 명시
        if (data !== undefined) {
            headers['Content-Type'] = 'application/json;charset=UTF-8';
            headers['Accept'] = 'application/json';
        }

        // Thymeleaf(Spring Security) CSRF 메타태그 자동 첨부
        const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        if (csrfTokenMeta && csrfHeaderMeta) {
            headers[csrfHeaderMeta.content] = csrfTokenMeta.content;
        }

        const res = await fetch(url, {
            method,
            headers,
            body: data !== undefined ? JSON.stringify(data) : undefined,
        });

        // 204(No Content) 처리
        if (res.status === 204) return null;

        if (!res.ok) {
            let detail = '';
            try {
                const ct = res.headers.get('content-type') || '';
                if (ct.includes('application/json')) {
                    const j = await res.json();
                    detail = j.message || JSON.stringify(j);
                } else {
                    detail = await res.text();
                }
            } catch (_) {}
            throw new Error(`HTTP ${res.status} ${res.statusText}\n${detail}`);
        }

        const ct = res.headers.get('content-type') || '';
        if (ct.includes('application/json')) return res.json();
        return res.text().catch(() => null);
    }

    // 버튼 중복 클릭 방지
    function withBusy(btn, fn) {
        return async function (e) {
            e && e.preventDefault();
            if (btn && btn.dataset.busy === '1') return;
            try {
                if (btn) {
                    btn.dataset.busy = '1';
                    btn.disabled = true;
                }
                await fn();
            } finally {
                if (btn) {
                    btn.disabled = false;
                    btn.dataset.busy = '0';
                }
            }
        };
    }

    // 글 등록
    async function save() {
        const title = ($id('title')?.value || '').trim();
        const author = ($id('author')?.value || '').trim();
        const content = ($id('content')?.value || '').trim();

        if (!title || !author || !content) {
            alert('제목/작성자/내용을 모두 입력해 주세요.');
            return;
        }

        await http('/api/v1/posts', 'POST', { title, author, content });
        alert('글이 등록되었습니다.');
        window.location.assign('/');
    }

    // 글 수정
    async function update() {
        const id = ($id('id')?.value || '').trim();
        const title = ($id('title')?.value || '').trim();
        const content = ($id('content')?.value || '').trim();

        if (!id) {
            alert('게시글 ID가 없습니다.');
            return;
        }
        if (!title || !content) {
            alert('제목/내용을 입력해 주세요.');
            return;
        }

        await http(`/api/v1/posts/${encodeURIComponent(id)}`, 'PUT', { title, content });
        alert('글이 수정되었습니다.');
        window.location.assign('/');
    }

    // 글 삭제
    async function remove() {
        const id = ($id('id')?.value || '').trim();
        if (!id) {
            alert('게시글 ID가 없습니다.');
            return;
        }
        if (!confirm('정말 삭제하시겠습니까?')) return;

        await http(`/api/v1/posts/${encodeURIComponent(id)}`, 'DELETE');
        alert('글이 삭제되었습니다.');
        window.location.assign('/');
    }

    // 이벤트 바인딩 (페이지별로 요소가 있을 때만 바인딩)
    document.addEventListener('DOMContentLoaded', function () {
        // 등록 폼이 있는 경우(예: posts-save.html)
        const form = $id('postForm');
        if (form) {
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                const btn = $id('btn-save');
                withBusy(btn, save)();
            });
        }

        const btnSave = $id('btn-save');
        if (btnSave) btnSave.addEventListener('click', withBusy(btnSave, save));

        // 수정/삭제 버튼이 있는 경우(예: posts-update.html)
        const btnUpdate = $id('btn-update');
        if (btnUpdate) btnUpdate.addEventListener('click', withBusy(btnUpdate, update));

        const btnDelete = $id('btn-delete');
        if (btnDelete) btnDelete.addEventListener('click', withBusy(btnDelete, remove));
    });
})();
