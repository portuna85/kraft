import { build } from 'vite';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * 템플릿의 <link rel="modulepreload">가 그 페이지의 진입 스크립트가 의존하는 청크를 빠짐없이 미리 받도록 되어 있는지 검증한다. 안 쓰는 청크를 preload하는 것도 실패다.
 *
 * 진입 스크립트를 파싱한 뒤에야 발견되는 의존 청크는 2단 워터폴이 되고, 작은 청크(1KB짜리 flash·toast 등)도 왕복을 한 번씩 만든다. Vue 진입 스크립트가 있는 모든 페이지가 대상이며
 * preload가 없는 페이지도 실패시킨다. 청크 크기는 문자열 길이(UTF-16 코드 유닛 수)가 아니라 실제 전송 바이트 수(UTF-8)로 비교한다.
 * 태그 속성 순서에 덜 의존하도록 태그 전체를 먼저 찾은 뒤 그 안에서 속성을 따로 찾는다(따옴표 형태까지 구조 기반으로 만들려면 별도 HTML 파서가 필요해 범위 밖).
 *
 * 템플릿은 정적 자원 장기 캐시를 위해 th:href/th:src의 @{...}를 쓰므로, 렌더링 전 템플릿 소스에 남은 @{...} 안쪽 경로를 매칭한다 — 실제 버전 접두사는 응답 시점에 Spring이 붙인다.
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
const HREF_RE = /\bth:href\s*=\s*"@\{\/js\/vue-dist\/(chunks\/[\w.-]+\.js)}"/;
const SRC_RE = /\bth:src\s*=\s*"@\{\/js\/vue-dist\/([\w.-]+\.js)}"/;

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

    // 이 페이지의 진입 스크립트들이 의존하는 모든 청크를 구한다. 하나라도 preload되어 있지 않으면 진입 스크립트를 파싱한 뒤에야 발견되어 2단 워터폴이 된다(preload가 없는 페이지 포함).
    // 가장 무거운 청크만이 아니라 전부 보는 이유: 작은 청크(1KB짜리 flash·toast 등)도 왕복 한 번씩을 만든다.
    const allDeps = new Set();
    for (const entry of entries) {
        for (const dep of transitiveChunkDeps(graph, entry)) {
            allDeps.add(dep);
        }
    }
    const missing = [...allDeps]
        .filter((dep) => !preloads.has(dep))
        .sort((a, b) => graph.get(b).size - graph.get(a).size);
    if (missing.length > 0) {
        const preloadList = preloads.size > 0 ? [...preloads].join(', ') : '(없음)';
        const missingList = missing.map((dep) => `${dep}(${graph.get(dep).size}B)`).join(', ');
        console.error(
            `${file}: 진입 스크립트(${entries.join(', ')})가 의존하는 청크 중 preload되지 않은 것: `
            + `${missingList}. 현재 preload: ${preloadList}.`,
        );
        failed = true;
    }
    // 이 페이지가 쓰지 않는 청크를 preload하면 안 쓸 바이트를 받는다.
    for (const preload of preloads) {
        if (graph.has(preload) && !allDeps.has(preload)) {
            console.error(`${file}: ${preload}를 preload하지만 이 페이지의 진입 스크립트는 그 청크를 import하지 않는다.`);
            failed = true;
        }
    }
}

if (failed) {
    process.exit(1);
}
console.log('모든 페이지가 진입 스크립트의 의존 청크를 빠짐없이(그리고 그것만) modulepreload한다.');
