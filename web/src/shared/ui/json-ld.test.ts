import { describe, expect, it } from "vitest";

import { serializeJsonLd } from "./json-ld";

describe("JSON-LD 직렬화", () => {
  it("스크립트 태그를 닫을 수 있는 <를 이스케이프한다", () => {
    // 게시글 제목처럼 사용자가 쓴 값이 JSON-LD에 들어가면 </script>가 그대로 나가서
    // 거기서 스크립트가 닫히고 뒤가 마크업으로 해석된다.
    const output = serializeJsonLd({ name: "</script><img src=x onerror=alert(1)>" });

    expect(output).not.toContain("</script>");
    expect(output).not.toContain("<img");
    expect(output).toContain("\\u003c");
  });

  it("이스케이프해도 값 자체는 보존한다", () => {
    const data = { name: "1 < 2", nested: { list: [1, 2] } };
    expect(JSON.parse(serializeJsonLd(data))).toEqual(data);
  });

  it("평범한 구조화 데이터는 그대로 직렬화한다", () => {
    const output = serializeJsonLd({ "@type": "FAQPage", mainEntity: [] });
    expect(JSON.parse(output)).toEqual({ "@type": "FAQPage", mainEntity: [] });
  });
});
