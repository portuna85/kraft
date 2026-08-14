/**
 * JSON-LD 삽입
 *
 * 이 프로젝트에서 `dangerouslySetInnerHTML`이 허용되는 두 곳 중 하나다(다른 하나는
 * 테마 부트스트랩 스크립트). 구조화 데이터는 `<script type="application/ld+json">`
 * 안에 원문 그대로 들어가야 해서 다른 방법이 없다.
 *
 * **nonce가 필수 인자다.** CSP가 nonce 기반이라 없으면 이 스크립트는 브라우저에서
 * 조용히 차단되고, 색인에서 구조화 데이터만 사라진 것을 한참 뒤에 알게 된다.
 */
export function JsonLd({ data, nonce }: { data: unknown; nonce: string | undefined }) {
  return (
    <script
      type="application/ld+json"
      nonce={nonce}
      suppressHydrationWarning
      dangerouslySetInnerHTML={{ __html: serializeJsonLd(data) }}
    />
  );
}

/**
 * `<` 를 유니코드 이스케이프한다.
 *
 * 데이터에 `</script>` 문자열이 들어가면 브라우저가 거기서 스크립트 태그를 닫는다 —
 * 게시글 제목처럼 사용자가 쓴 값이 JSON-LD에 들어가는 순간 XSS 통로가 된다. JSON
 * 문법에서 <는 `<`와 같은 값이라 구조화 데이터의 의미는 그대로다.
 */
export function serializeJsonLd(data: unknown): string {
  return JSON.stringify(data).replace(/</g, "\\u003c");
}
