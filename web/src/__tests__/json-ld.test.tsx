import { describe, expect, it } from "vitest";
import { render } from "@testing-library/react";
import { JsonLdBreadcrumb, JsonLdDiscussionForumPosting, JsonLdWebSite } from "@/components/json-ld";

function getScriptJson(container: HTMLElement) {
  const script = container.querySelector('script[type="application/ld+json"]');
  expect(script).not.toBeNull();
  return JSON.parse(script!.innerHTML);
}

describe("JSON-LD 컴포넌트", () => {
  it("JsonLdWebSite는 baseUrl 기준 WebSite/Organization 그래프를 nonce와 함께 렌더링한다", () => {
    const { container } = render(<JsonLdWebSite baseUrl="https://kraft.example" nonce="abc123" />);

    const script = container.querySelector('script[type="application/ld+json"]');
    expect(script).toHaveAttribute("nonce", "abc123");

    const parsed = getScriptJson(container);
    expect(parsed["@context"]).toBe("https://schema.org");
    const website = parsed["@graph"].find((node: { "@type": string }) => node["@type"] === "WebSite");
    expect(website.url).toBe("https://kraft.example");
    expect(website.inLanguage).toBe("ko-KR");
  });

  it("JsonLdBreadcrumb는 홈을 1번으로 두고 이후 항목을 순서대로 번호 매긴다", () => {
    const { container } = render(
      <JsonLdBreadcrumb
        baseUrl="https://kraft.example"
        items={[
          { name: "통계", item: "https://kraft.example/stats" },
          { name: "빈도 분석" },
        ]}
      />
    );

    const parsed = getScriptJson(container);
    expect(parsed["@type"]).toBe("BreadcrumbList");
    expect(parsed.itemListElement).toEqual([
      { "@type": "ListItem", position: 1, name: "홈", item: "https://kraft.example" },
      { "@type": "ListItem", position: 2, name: "통계", item: "https://kraft.example/stats" },
      { "@type": "ListItem", position: 3, name: "빈도 분석" },
    ]);
  });

  it("항목에 item이 없으면 해당 필드를 아예 생략한다(빈 문자열로 채우지 않는다)", () => {
    const { container } = render(
      <JsonLdBreadcrumb baseUrl="https://kraft.example" items={[{ name: "저장 번호" }]} />
    );

    const parsed = getScriptJson(container);
    expect(parsed.itemListElement[1]).toEqual({ "@type": "ListItem", position: 2, name: "저장 번호" });
    expect("item" in parsed.itemListElement[1]).toBe(false);
  });

  it("항목 이름에 </script>가 섞여 있어도 스크립트를 조기 종료시키지 않는다(이스케이프 회귀 가드)", () => {
    const { container } = render(
      <JsonLdBreadcrumb
        baseUrl="https://kraft.example"
        items={[{ name: "</script><script>alert(1)</script>" }]}
      />
    );

    const script = container.querySelector('script[type="application/ld+json"]');
    // 실제로 태그가 조기 종료됐다면 이 스크립트 엘리먼트 자체가 잘려서 JSON 파싱이 깨진다.
    const parsed = getScriptJson(container);
    expect(parsed.itemListElement[1].name).toBe("</script><script>alert(1)</script>");
    expect(script!.innerHTML).not.toContain("<script>alert(1)</script>");
  });

  it("JsonLdDiscussionForumPosting은 게시글별 필드를 DiscussionForumPosting 스키마로 렌더링한다", () => {
    const { container } = render(
      <JsonLdDiscussionForumPosting
        baseUrl="https://kraft.example"
        nonce="abc123"
        url="https://kraft.example/community/posts/8"
        headline="자주 나온 번호와 오래 안 나온 번호, 어느 쪽이 더 유리할까요?"
        text="본문 내용입니다."
        authorName="닉네임"
        datePublished="2026-07-24T09:29:00Z"
        dateModified="2026-07-24T09:30:00Z"
        commentCount={2}
      />
    );

    const script = container.querySelector('script[type="application/ld+json"]');
    expect(script).toHaveAttribute("nonce", "abc123");

    const parsed = getScriptJson(container);
    expect(parsed["@type"]).toBe("DiscussionForumPosting");
    expect(parsed.headline).toBe("자주 나온 번호와 오래 안 나온 번호, 어느 쪽이 더 유리할까요?");
    expect(parsed.url).toBe("https://kraft.example/community/posts/8");
    expect(parsed.commentCount).toBe(2);
    expect(parsed.author).toEqual({ "@type": "Person", name: "닉네임" });
  });
});
