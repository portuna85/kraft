import { describe, expect, it } from "vitest";
import { serializeJsonLd } from "@/lib/json-ld-serialize";

describe("JSON-LD 직렬화", () => {
  it("일반 객체는 JSON.stringify와 동일하게 직렬화한다", () => {
    expect(serializeJsonLd({ a: 1, b: "text" })).toBe('{"a":1,"b":"text"}');
  });

  it("</script> 같은 값에 포함된 '<'를 이스케이프해 태그 조기 종료를 막는다", () => {
    const result = serializeJsonLd({ name: "</script><script>evil()</script>" });

    expect(result).not.toContain("<");
    expect(result).toBe('{"name":"\\u003c/script>\\u003cscript>evil()\\u003c/script>"}');
  });
});
