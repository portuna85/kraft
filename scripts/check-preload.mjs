import { build } from 'vite';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 템플릿의 <link rel="modulepreload">가 실제로 그 페이지의 "무거운" 공유 청크를 미리 받도록
 * 되어 있는지 검증한다.
 *
 * F07: 5개 템플릿이 "Vue 런타임을 포함한 공유 청크"라며 chunks/flash.js(1.3KB)를 preload했지만,
 * 실제 런타임은 훨씬 큰 다른 청크(당시 chunks/http.js, 67KB)에 있었다. flash.js도 그 큰 청크를
 * import하는 진짜 의존성이라 "이 페이지가 쓰는 청크인가?"만 확인하면 이 버그를 잡지 못한다 —
 * 각 템플릿이 실제로 preload해야 하는 것은 그 페이지의 진입 스크립트가 의존하는 청크 중 가장
 * 무거운 것이다. vite.config.js가 그 청크를 "runtime"으로 고정해 두지만, 이 스크립트는 이름이
 * 아니라 실제 크기로 판단해 이름이 다시 바뀌어도 계속 유효하다.
 *
 * F01: 처음에는 preload 링크가 하나도 없는 페이지를 그냥 건너뛰었다 — Vue 진입 스크립트가
 * 있는데 preload가 아예 없는 페이지(예: recommend.html)는 "누락됨"으로 잡히지 않고 조용히
 * 통과했다. 이제 Vue 진입 스크립트가 있는 모든 페이지를 대상으로, preload가 없거나 가장
 * 무거운 청크를 가리키지 않으면 실패시킨다. 청크 크기도 문자열 길이(UTF-16 코드 유닛 수)가
 * 아니라 실제 전송 바이트 수(UTF-8)로 비교한다 — 멀티바이트 문자가 섞인 코드에서는 둘이
 * 다르다. 태그 속성 순서에도 덜 의존하도록, 태그 전체를 먼저 찾은 뒤 그 안에서 속성을 따로
 * 찾는다(따옴표 형태까지 완전히 구조 기반으로 만드는 것은 별도 HTML 파서가 필요해 범위 밖).
 */
const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(__dirname, '..');
const templatesDir = join(repoRoot, 'src/main/resources/templates');

function listHtmlFiles(dir) {
    const out = [];
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const full = join(dir, entry.name);
        if (entry.isDirectory()) {
            out.push(...listHtmlFiles(full));
        } else if (entry.name.endsWith('.html')) {
            out.push(full);
        }
    }
    return out;
}

/** 출력 청크 파일명 -> { imports: Set<파일명>, size: number(전송 바이트 수, UTF-8) }. */
function buildGraph(bundle) {
    const graph = new Map();
    for (const chunk of Object.values(bundle)) {
        if (chunk.type === 'chunk') {
            graph.set(chunk.fileName, { imports: new Set(chunk.imports ?? []), size: Buffer.byteLength(chunk.code, 'utf8') });
        }
    }
    return graph;
}

/** entryFile이 직접·간접으로 의존하는 다른 청크 파일명(entryFile 자신은 제외). */
function transitiveChunkDeps(graph, entryFile) {
    const seen = new Set();
    const stack = [...(graph.get(entryFile)?.imports ?? [])];
    while (stack.length > 0) {
        const file = stack.pop();
        if (seen.has(file)) {
            continue;
        }
        seen.add(file);
        for (const dep of graph.get(file)?.imports ?? []) {
            stack.push(dep);
        }
    }
    return seen;
}

// 태그 전체를 먼저 찾고, 그 안에서 rel/href·type/src를 속성 순서와 무관하게 따로 찾는다.
const LINK_TAG_RE = /<link\b[^>]*>/g;
const SCRIPT_TAG_RE = /<script\b[^>]*>/g;
const REL_MODULEPRELOAD_RE = /\brel\s*=\s*"modulepreload"/;
const TYPE_MODULE_RE = /\btype\s*=\s*"module"/;
const HREF_RE = /\bhref\s*=\s*"\/js\/vue-dist\/(chunks\/[\w.-]+\.js)"/;
const SRC_RE = /\bsrc\s*=\s*"\/js\/vue-dist\/([\w.-]+\.js)"/;

function findPreloadedChunks(html) {
    const found = new Set();
    for (const [tag] of html.matchAll(LINK_TAG_RE)) {
        if (!REL_MODULEPRELOAD_RE.test(tag)) {
            continue;
        }
        const hrefMatch = tag.match(HREF_RE);
        if (hrefMatch) {
            found.add(hrefMatch[1]);
        }
    }
    return found;
}

function findVueEntries(html) {
    const found = [];
    for (const [tag] of html.matchAll(SCRIPT_TAG_RE)) {
        if (!TYPE_MODULE_RE.test(tag)) {
            continue;
        }
        const srcMatch = tag.match(SRC_RE);
        if (srcMatch) {
            found.push(srcMatch[1]);
        }
    }
    return found;
}

const result = await build({
    configFile: join(repoRoot, 'src/vue/vite.config.js'),
    build: { write: false },
    logLevel: 'silent',
});
const bundle = Array.isArray(result) ? result[0].output : result.output;
const graph = buildGraph(bundle);

let failed = false;

for (const file of listHtmlFiles(templatesDir)) {
    const html = readFileSync(file, 'utf8');
    const entries = findVueEntries(html);
    if (entries.length === 0) {
        // Vue 진입 스크립트가 아예 없는 페이지는 preload할 것도 없다.
        continue;
    }
    const preloads = findPreloadedChunks(html);

    for (const preload of preloads) {
        if (!graph.has(preload)) {
            console.error(`${file}: ${preload}가 현재 빌드 출력에 없다.`);
            failed = true;
        }
    }

    // 이 페이지의 진입 스크립트들이 의존하는 모든 청크 중 가장 무거운 것을 구한다 — 그것이
    // preload할 가치가 있는 청크다. 실제로 preload되어 있는지 확인한다(preload가 하나도
    // 없는 페이지도 여기 포함된다 — F01).
    const allDeps = new Set();
    for (const entry of entries) {
        for (const dep of transitiveChunkDeps(graph, entry)) {
            allDeps.add(dep);
        }
    }
    if (allDeps.size === 0) {
        continue;
    }
    const heaviest = [...allDeps].sort((a, b) => graph.get(b).size - graph.get(a).size)[0];
    if (!preloads.has(heaviest)) {
        const preloadList = preloads.size > 0 ? [...preloads].join(', ') : '(없음)';
        console.error(
            `${file}: ${preloadList}를 preload하지만, 이 페이지가 실제로 의존하는 `
            + `가장 무거운 청크는 ${heaviest}(${graph.get(heaviest).size}B)다.`,
        );
        failed = true;
    }
}

if (failed) {
    process.exit(1);
}
console.log('modulepreload 참조가 실제로 가장 무거운 의존 청크를 가리킨다.');
