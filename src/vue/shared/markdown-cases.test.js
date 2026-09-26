// 13단계: 서버(MarkdownParserTest)와 클라이언트(이 파일)가 같은 공용 픽스처로 같은 결과를
// 내는지 확인한다. 그때그때의 세부 규칙(중첩, 곱셈 표기 등)은 markdown.test.js가 이미
// 촘촘히 검증하므로, 여기서는 "SSR과 Vue가 같은 문법을 같은 AST로 해석한다"는 사실 자체만
// 픽스처 전체를 훑어 확인한다. 로직을 바꿀 때는 markdown.js뿐 아니라
// com.kraft.post.markdown.MarkdownParser도 함께 고치고, 두 테스트가 같은 픽스처로 여전히
// 일치하는지 확인해야 한다.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { parseMarkdown } from './markdown.js';

const fixturePath = path.join(
    path.dirname(fileURLToPath(import.meta.url)),
    '../../test/resources/markdown/cases.json',
);
const cases = JSON.parse(readFileSync(fixturePath, 'utf-8'));

for (const { name, input, expected } of cases) {
    test(`공용 픽스처: ${name}`, () => {
        assert.deepEqual(parseMarkdown(input), expected);
    });
}
