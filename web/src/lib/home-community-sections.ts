type PostWithId = { id: number };

export const HOME_LATEST_POST_LIMIT = 4;
export const HOME_POPULAR_POST_LIMIT = 3;

// KX-09: 실제 화면에 보이는 인기 글이 최신 글 목록에 모두 포함될 때만 중복 목록을
// 생략한다. API 전체 집합이 같더라도 정렬 순서 때문에 최신 글의 표시 범위 밖에 있는
// 인기 글은 숨기지 않는다.
export function communityPopularPostsAlreadyVisible(
  latestPosts: PostWithId[],
  weeklyPopularPosts: PostWithId[],
): boolean {
  const visiblePopularPosts = weeklyPopularPosts.slice(0, HOME_POPULAR_POST_LIMIT);
  if (visiblePopularPosts.length === 0) return false;

  const visibleLatestIds = new Set(
    latestPosts.slice(0, HOME_LATEST_POST_LIMIT).map((post) => post.id),
  );
  return visiblePopularPosts.every((post) => visibleLatestIds.has(post.id));
}
