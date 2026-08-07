import { describe, expect, it } from "vitest";
import {
  INFO_PAGE_METADATA,
  INFO_PAGE_SLUGS,
  isInfoPageSlug,
} from "@/lib/info-page-metadata";

describe("information page metadata contract", () => {
  it("has unique slugs and complete sitemap metadata", () => {
    expect(new Set(INFO_PAGE_SLUGS).size).toBe(INFO_PAGE_SLUGS.length);
    expect(INFO_PAGE_SLUGS).toEqual(Object.keys(INFO_PAGE_METADATA));

    for (const slug of INFO_PAGE_SLUGS) {
      expect(INFO_PAGE_METADATA[slug]).toMatchObject({
        title: expect.any(String),
        description: expect.any(String),
        lastModified: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        priority: expect.any(Number),
      });
    }
  });

  it("guards valid and invalid slugs", () => {
    expect(isInfoPageSlug("faq")).toBe(true);
    expect(isInfoPageSlug("not-a-page")).toBe(false);
  });
});
