// /src/main/resources/static/js/app/index.js
// jQuery → 바닐라 JS + Fetch 버전 (Spring Security CSRF 메타 태그 있으면 자동 적용)

const $ = (sel) => document.querySelector(sel);
const val = (sel) => (($(sel) || {}).value || "").trim();

function getCsrf() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");
    return token && header ? { header, token } : null;
}

async function http(method, url, data) {
    const headers = { "Content-Type": "application/json; charset=UTF-8" };
    const csrf = getCsrf();
    if (csrf) headers[csrf.header] = csrf.token;

    const res = await fetch(url, {
        method,
        headers,
        body: data ? JSON.stringify(data) : undefined,
    });

    if (!res.ok) {
        const text = await res.text().catch(() => "");
        throw new Error(text || `${res.status} ${res.statusText}`);
    }
    // 성공 시 응답이 비어있을 수도 있으므로 안전 처리
    const ct = res.headers.get("content-type") || "";
    return ct.includes("application/json") ? res.json() : res.text();
}

const main = {
    init() {
        $("#btn-save")?.addEventListener("click", () => this.save());
        $("#btn-update")?.addEventListener("click", () => this.update());
        $("#btn-delete")?.addEventListener("click", () => this.remove());
    },

    async save() {
        const data = {
            title: val("#title"),
            author: val("#author"),
            content: val("#content"),
        };

        try {
            await http("POST", "/api/v1/posts", data);
            alert("글이 등록되었습니다.");
            location.assign("/");
        } catch (e) {
            alert(`등록 실패: ${e.message}`);
        }
    },

    async update() {
        const id = val("#id");
        if (!id) return alert("ID를 찾을 수 없습니다.");

        const data = {
            title: val("#title"),
            content: val("#content"),
        };

        try {
            await http("PUT", `/api/v1/posts/${encodeURIComponent(id)}`, data);
            alert("글이 수정되었습니다.");
            location.assign("/");
        } catch (e) {
            alert(`수정 실패: ${e.message}`);
        }
    },

    async remove() {
        const id = val("#id");
        if (!id) return alert("ID를 찾을 수 없습니다.");
        if (!confirm("정말 삭제하시겠습니까?")) return;

        try {
            await http("DELETE", `/api/v1/posts/${encodeURIComponent(id)}`);
            alert("글이 삭제되었습니다.");
            location.assign("/");
        } catch (e) {
            alert(`삭제 실패: ${e.message}`);
        }
    },
};

document.addEventListener("DOMContentLoaded", () => main.init());
