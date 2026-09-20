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

/** 출력 청크 파일명 -> { imports: Set<파일명>, size: number(코드 바이트) }. */
function buildGraph(bundle) {
    const graph = new Map();
    for (const chunk of Object.values(bundle)) {
        if (chunk.type === 'chunk') {
            graph.set(chunk.fileName, { imports: new Set(chunk.imports ?? []), size: chunk.code.length });
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

const PRELOAD_RE = /<link rel="modulepreload" href="\/js\/vue-dist\/(chunks\/[\w.-]+\.js)"/g;
const SCRIPT_RE = /<script type="module" src="\/js\/vue-dist\/([\w.-]+\.js)">/g;

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
    const preloads = new Set([...html.matchAll(PRELOAD_RE)].map((m) => m[1]));
    if (preloads.size === 0) {
        continue;
    }

    const entries = [...html.matchAll(SCRIPT_RE)].map((m) => m[1]);
    if (entries.length === 0) {
        console.error(`${file}: modulepreload은 있지만 Vue 진입 스크립트를 찾지 못했다.`);
        failed = true;
        continue;
    }

    for (const preload of preloads) {
        if (!graph.has(preload)) {
            console.error(`${file}: ${preload}가 현재 빌드 출력에 없다.`);
            failed = true;
        }
    }

    // 이 페이지의 진입 스크립트들이 의존하는 모든 청크 중 가장 무거운 것을 구한다 — 그것이
    // preload할 가치가 있는 청크다. 실제로 preload되어 있는지 확인한다.
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
        console.error(
            `${file}: ${[...preloads].join(', ')}를 preload하지만, 이 페이지가 실제로 의존하는 `
            + `가장 무거운 청크는 ${heaviest}(${graph.get(heaviest).size}B)다.`,
        );
        failed = true;
    }
}

if (failed) {
    process.exit(1);
}
console.log('modulepreload 참조가 실제로 가장 무거운 의존 청크를 가리킨다.');
