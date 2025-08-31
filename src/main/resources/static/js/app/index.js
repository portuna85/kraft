// src/main/resources/static/js/app/index.js
(function () {
    'use strict';

    const $id = (id) => document.getElementById(id);

    async function http(url, method, data) {
        const headers = {'Content-Type': 'application/json;charset=UTF-8'};

        // Spring Security CSRF 메타태그가 있으면 자동 첨부
        const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
        const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        if (csrfTokenMeta && csrfHeaderMeta) {
            headers[csrfHeaderMeta.content] = csrfTokenMeta.content;
        }

        const res = await fetch(url, {
            method,
            headers,
            body: data ? JSON.stringify(data) : undefined,
        });

        if (!res.ok) {
            const body = await res.text().catch(() => '');
            throw new Error(`HTTP ${res.status} ${res.statusText}\n${body}`);
        }

        const ct = res.headers.get('content-type') || '';
        if (ct.includes('application/json')) return res.json();
        return res.text().catch(() => null);
    }

    function withBusy(btn, fn) {
        return async function (e) {
            e && e.preventDefault();
            if (btn?.dataset.busy === '1') return;
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

    async function save() {
        const title = ($id('title')?.value || '').trim();
        const author = ($id('author')?.value || '').trim();
        const content = ($id('content')?.value || '').trim();

        if (!title || !author || !content) {
            alert('제목/작성자/내용을 모두 입력해 주세요.');
            return;
        }

        await http('/api/v1/posts', 'POST', {title, author, content});
        alert('글이 등록되었습니다.');
        window.location.assign('/');
    }

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

        await http(`/api/v1/posts/${encodeURIComponent(id)}`, 'PUT', {title, content});
        alert('글이 수정되었습니다.');
        window.location.assign('/');
    }

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

    document.addEventListener('DOMContentLoaded', function () {
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

        const btnUpdate = $id('btn-update');
        if (btnUpdate) btnUpdate.addEventListener('click', withBusy(btnUpdate, update));

        const btnDelete = $id('btn-delete');
        if (btnDelete) btnDelete.addEventListener('click', withBusy(btnDelete, remove));
    });
})();
