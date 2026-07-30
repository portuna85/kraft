type PostWithId = { id: number };

// KX-09: 글이 적으면 "최신 글"과 "주간 인기"가 같은 글 목록을 그대로 반복해 두 섹션이
// 같은 정보를 두 번 보여주는 것처럼 보인다. 두 목록의 글 id 집합이 완전히 같을 때만
// (둘 다 비어 있는 경우 포함) true를 반환한다 — 글이 늘어 목록이 달라지면 자동으로
// 다시 분리된다.
export function communitySectionsIdentical(latestPosts: PostWithId[], weeklyPopularPosts: PostWithId[]): boolean {
  if (latestPosts.length !== weeklyPopularPosts.length) return false;
  const latestIds = new Set(latestPosts.map((post) => post.id));
  return weeklyPopularPosts.every((post) => latestIds.has(post.id));
}
