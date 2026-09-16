import { test, expect, storageStateFor, uniqueTitle, openPostByTitle } from './fixtures.js';

test.use({ storageState: storageStateFor('user') });

/**
 * 제목·본문·댓글은 Vue 아일랜드의 초기 상태로 {@code <script type="application/json">}에
 * 그대로 끼워져 내려간다(post-save.html, post-update.html). 그 값에 {@code </script>}가
 * 있으면 브라우저가 HTML을 파싱하는 시점에 script 요소가 거기서 끝나 버려, 뒤에 오는 내용이
 * 그대로 실행 가능한 HTML/스크립트가 될 수 있었다(개선 보고서 "JSON을 HTML script에 넣는 경계
 * 검증 부족" — 확인해 보니 실제로 저장형 XSS였다). 서버가 `<` 형태로 이스케이프해
 * 막았는지, 화면에는 페이로드가 그대로 "글자"로만 보이는지 확인한다.
 */
test('제목·본문·댓글에 </script>가 있어도 스크립트가 실행되지 않고 글자 그대로 보인다', async ({ page }) => {
    const payload = '</script><img src=x onerror="window.__xssFired = true">';
    const title = uniqueTitle('XSS') + payload;

    await page.goto('/posts/save');
    await page.locator('#title').fill(title);
    await page.locator('#content').fill(payload);
    await page.locator('#btn-save').click();
    await page.waitForURL('/');
    await openPostByTitle(page, title);

    await expect(page.locator('#post-title-text')).toHaveText(title);
    await expect(page.locator('#post-content-text')).toHaveText(payload);
    // HTML로 해석됐다면 <img>가 실제 요소로 렌더링됐을 것이다.
    await expect(page.locator('#post-content-text img')).toHaveCount(0);

    await page.locator('#comment-content').fill(payload);
    await page.locator('#btn-comment-save').click();
    await expect(page.locator('.comment-list__content')).toContainText(payload);
    await expect(page.locator('.comment-list__content img')).toHaveCount(0);

    expect(await page.evaluate(() => window.__xssFired === true),
        '주입된 스크립트가 실행되면 안 된다').toBe(false);

    // 새로고침(초기 JSON을 다시 파싱하는 경로)해도 안전해야 한다.
    await page.reload();
    expect(await page.evaluate(() => window.__xssFired === true)).toBe(false);
    await expect(page.locator('#post-content-text')).toHaveText(payload);
});
